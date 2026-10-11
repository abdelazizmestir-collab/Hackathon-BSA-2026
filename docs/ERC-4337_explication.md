# Smart contracts, Account Abstraction (ERC-4337) et Pimlico — expliqué simplement

## 1. Un smart contract, c'est quoi ?

Un **smart contract** est un programme déployé sur la blockchain, à une adresse (`0x…`).
Il a du code (immuable) et un état (des variables), et n'importe qui peut l'appeler.
Exemples dans ce projet :

| Contrat | Rôle |
|---|---|
| **USDC** (`0x036C…CF7e` sur Base Sepolia) | un token ERC-20 : tient un tableau `solde[adresse]` + une fonction `transfer(to, amount)` |
| **EntryPoint v0.6** (`0x5FF1…2789`) | le contrat central d'ERC-4337, le même sur toutes les chaînes (version utilisée par Coinbase Smart Wallet) |
| **Coinbase Smart Wallet Factory** (`0x0BA5…428a`) | une « usine » qui déploie des wallets-contrats |
| **SwissRecoveryModule** (`0xB378…428F`) | notre contrat de récupération sociale (3 garants sur 4 + délai) |
| **Coinbase Smart Wallet** (ton wallet) | **ton wallet est lui-même un smart contract** |

Pour appeler une fonction, on envoie des **données encodées en ABI** :
`keccak256("transfer(address,uint256)")` → 4 premiers octets `0xa9059cbb` (le *selector*),
puis les arguments sur 32 octets chacun. C'est exactement ce que fait `data/aa/Abi.kt`.

## 2. Le problème des wallets « classiques » (EOA)

Un wallet MetaMask classique = un **EOA** (*Externally Owned Account*) = juste une clé privée.

* Si tu perds la clé → tu perds tout (pas de récupération).
* Pour envoyer quoi que ce soit, tu dois **avoir de l'ETH pour payer le gas**, même pour envoyer de l'USDC.
* Une seule règle de sécurité possible : « la signature est valide ».

## 3. L'Account Abstraction (l'« abstraction »)

**Idée** : le compte n'est plus une clé, c'est **un smart contract**. C'est *lui* qui décide ce
qu'est une transaction valide. La clé n'est plus qu'un moyen parmi d'autres de l'autoriser.
On a « abstrait » la notion de compte : la logique de validation est programmable.

Ce que ça permet :

* **Gas sponsorisé** : quelqu'un d'autre (un *paymaster*) paie les frais → l'utilisateur n'a pas besoin d'ETH.
* Récupération sociale, multi-signatures, limites de dépenses, sessions, biométrie (passkeys)…
* Batch : plusieurs actions en une seule opération.
* Adresse connue **avant** le déploiement (CREATE2) → on peut recevoir des fonds tout de suite.

**ERC-4337** est la norme qui rend ça possible **sans modifier Ethereum** : au lieu d'envoyer une
transaction, on envoie une **UserOperation** à un réseau parallèle.

## 4. Les acteurs d'ERC-4337

```
 ┌───────────────┐   UserOperation    ┌──────────────┐   handleOps([...])   ┌──────────────┐
 │  SwissWallet  │ ─────────────────▶ │   BUNDLER    │ ───────────────────▶ │  EntryPoint  │
 │ (app Android) │                    │  (Pimlico)   │  vraie transaction   │   (contrat)  │
 └──────┬────────┘                    └──────────────┘                      └──────┬───────┘
        │ pm_sponsorUserOperation                                     1. validateUserOp │ → Coinbase Smart Wallet
        ▼                                                             2. validatePaymaster │ → Paymaster
 ┌──────────────┐                                                     3. execute(to, value, data)
 │  PAYMASTER   │  « je paie le gas de cette opération » (signature)
 │  (Pimlico)   │
 └──────────────┘
```

| Acteur | Dans le code | Rôle |
|---|---|---|
| **UserOperation** | `domain/aa/AccountAbstraction.kt` | l'« intention » : qui (`sender`), quoi (`callData`), nonce, gas, paymaster, signature |
| **Smart Account** | `CoinbaseSmartAccount.kt` | le contrat-wallet (Coinbase Smart Wallet) ; vérifie la signature d'un de ses owners et exécute `execute(to, value, data)` |
| **Passkey** | `AndroidPasskeyVault.kt` + `WebAuthnAssertion.kt` | clé P-256 générée **dans la puce sécurisée** (Android Keystore), jamais exportable ; elle ne fait que **signer** (format WebAuthn) |
| **Bundler** | `PimlicoClient` (`eth_sendUserOperation`) | récupère les UserOps, les regroupe et envoie *une* vraie transaction à l'EntryPoint (il avance le gas) |
| **Paymaster** | `PimlicoClient` (`pm_sponsorUserOperation`) | contrat qui rembourse le gas au bundler à la place de l'utilisateur |
| **EntryPoint** | `ChainConfig.ENTRY_POINT_V06` | le contrat « arbitre » : valide, exécute, fait payer le bon acteur |
| **Factory** | `ChainConfig.CSW_FACTORY_V1` | déploie ton wallet au premier envoi (champ `initCode`) |

## 5. Pimlico, concrètement

Pimlico est un **fournisseur d'infrastructure ERC-4337** : il fait tourner un bundler (*Alto*)
et des paymasters, accessibles via une simple URL JSON-RPC :

```
https://api.pimlico.io/v2/base-sepolia/rpc?apikey=TA_CLE
```

* **Bundler** : `eth_sendUserOperation`, `eth_getUserOperationReceipt`, `pimlico_getUserOperationGasPrice`.
* **Verifying paymaster** : `pm_sponsorUserOperation` → Pimlico simule ton opération, estime le gas,
  et renvoie `paymasterAndData` (son adresse + sa **signature** qui dit « je paie »). L'EntryPoint vérifie
  cette signature on-chain, puis débite le **dépôt de Pimlico**, pas ton wallet.
* Pimlico se rembourse sur **ton solde Pimlico** (dashboard). Sur les **testnets c'est gratuit**.
  En production tu crées une *sponsorship policy* (ex. : max 5 CHF/jour/utilisateur).

> « Gas free » = l'utilisateur ne paie pas. Quelqu'un paie quand même : le paymaster (donc toi / Swissquote).

## 6. Ce que fait l'app quand tu appuies sur « Confirm & send »

Fichiers : `data/aa/AaTransferService.kt` + `data/aa/UserOperationSender.kt` (le « tuyau » ERC-4337
partagé par toutes les actions on-chain de l'app). Chaque étape est affichée dans l'écran Send.

1. **Preparing** — lit le solde, le `nonce` (`EntryPoint.getNonce`), si le wallet est déjà déployé
   (`eth_getCode`) et le prix du gas. Construit le `callData` du **Coinbase Smart Wallet** :
   * ETH : `execute(destinataire, montant, 0x)`
   * USDC : `execute(USDC, 0, transfer(destinataire, montant))`
   * premier envoi : ajoute `initCode = factory ‖ createAccount([passkey, module], 0)` → déploiement automatique.
2. **Sponsoring** — `pm_sponsorUserOperation` (EntryPoint **v0.6**) avec une signature « stub » ;
   Pimlico renvoie les limites de gas + `paymasterAndData` (« c'est moi, Pimlico, qui paie »).
3. **Signing** — calcule le `userOpHash` puis le fait signer par la **passkey** (clé P-256 dans la puce
   sécurisée, Android Keystore). La signature est emballée au format **WebAuthn** (comme une passkey de
   navigateur) : le contrat vérifie on-chain que le `challenge` = userOpHash et que la signature P-256 est bonne.
4. **Submitting** — `eth_sendUserOperation` → le bundler renvoie le `userOpHash`.
5. **Waiting** — `eth_getUserOperationReceipt` toutes les 2 s → `transactionHash` réel → lien explorer.

## 7. Génération du wallet (Swissquote → SwissWallet)

1. Swissquote lance `ProvisionActivity` de SwissWallet (transparente), protégée par une **permission de
   signature** (seules nos apps peuvent l'appeler). Il envoie aussi **son adresse de garant**.
2. SwissWallet crée une **passkey** (P-256) dans le Keystore.
3. Elle calcule l'**adresse contrefactuelle** du Coinbase Smart Wallet
   (`factory.getAddress([passkey, module de récupération], 0)`) : le contrat n'existe pas encore, mais son
   adresse est déjà connue grâce à CREATE2.
4. Elle renvoie l'adresse à Swissquote → « Your wallet is ready! ».
5. Le contrat n'est déployé qu'à la **première opération** — et c'est le paymaster qui paie ce déploiement.

## 8. Récupération sociale

Voir **Recuperation_sociale.md** : 4 garants (Swissquote automatiquement, ton autre appareil, tes proches),
règle **3 sur 4**, délai de sécurité **30 s** en démo (48 h en vrai), annulation possible.

## 9. Limites (c'est un concept étudiant)

* Testnet uniquement (Base Sepolia par défaut). Pour le mainnet : backend qui garde la clé API Pimlico +
  sponsorship policy, clé de garant Swissquote dans un HSM côté serveur, vraie vérification d'identité.
* L'historique « reçu » n'est pas indexé (il faudrait un indexer) : l'app affiche tes envois réels + quelques
  entrées *Demo*.
* La passkey n'exige pas l'empreinte digitale dans la démo (émulateur) ; sur un vrai téléphone on
  activerait `setUserAuthenticationRequired(true)`.
