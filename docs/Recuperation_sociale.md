# Récupération sociale (3 garants sur 4) — expliqué simplement

## 1. Le problème

Ton wallet est contrôlé par une **passkey** : une clé enfermée dans la puce sécurisée de ton téléphone.
Personne ne peut la copier… même pas toi. Donc si tu perds le téléphone, la clé est perdue.

Avec un wallet classique (MetaMask), c'est fini : l'argent est bloqué pour toujours.
Avec un **smart wallet**, on peut prévoir une règle de secours.

## 2. L'image du coffre-fort

* Ton wallet = un **coffre-fort** (un smart contract **Coinbase Smart Wallet**). Son adresse ne change jamais.
* Le coffre a une liste de **propriétaires** (« owners ») :
  * **#0 ta passkey** → elle peut tout faire (envoyer, changer les garants, annuler une récupération) ;
  * **#1 le module de récupération** (`SwissRecoveryModule`) → il ne peut faire **qu'une seule chose** :
    ajouter une nouvelle passkey (et retirer la perdue), et **seulement si** les règles sont respectées.
* Les règles du module :
  1. **3 de tes 4 garants** doivent signer « oui, ce nouveau téléphone est bien à lui » ;
  2. puis il faut attendre un **délai de sécurité** : **30 secondes** pour la démo (**48 h** en vrai) ;
  3. pendant ce délai, ta passkey actuelle peut **annuler**.

Quand tout est OK, le coffre reçoit ta nouvelle clé. **Même adresse, même argent.**

## 3. Tes 4 garants

| # | Garant | Comment il est ajouté | Comment il dit « oui » |
|---|---|---|---|
| 1 | **Swissquote** (automatique) | Quand tu appuies sur *Generate my wallet*, Swissquote donne son adresse de garant à SwissWallet. Il est déjà dans la liste, impossible de l'enlever. | Sur le nouveau téléphone : *Ask Swissquote* → l'app Swissquote s'ouvre → elle vérifie que tu es connecté et que c'est bien **ton** wallet → **vérification d'identité** (2FA, selfie vs pièce d'identité, SMS — simulée dans la démo) → elle signe et te renvoie dans SwissWallet. |
| 2 | **Ton autre appareil** (tablette, ancien téléphone) | Sur cet appareil : SwissWallet → *Be a guardian* → *For my own wallet* → copie le **code garant** (`SWGRD1.…`). Sur ton téléphone : Profile → Guardians → *Add guardian* → *My other device* → colle. | Tu lui envoies ton **code de demande** (`SWREC1.…`). Il le colle dans *Be a guardian* → *Approve a recovery*, vérifie l'empreinte, appuie sur *Approve*, et te renvoie un **code d'approbation** (`SWSIG1.…`) que tu colles. |
| 3-4 | **Tes proches** (maman, un ami…) | Pareil, avec *For a close one*. Ils t'envoient leur code garant (ou juste leur adresse `0x…`). | Pareil. Conseil : **appelle-les** et comparez l'empreinte de la clé (ex. `3F2A-91C0`) avant qu'ils approuvent. |
| démo | **Garant simulé** | *Add guardian* → *Simulated guardian (demo)* : une clé gardée sur ce téléphone, pour tester avec **un seul émulateur**. | *Approve with simulated guardians (demo)*. En vrai, chaque clé serait sur le téléphone de quelqu'un d'autre. |

**Pourquoi 3 sur 4 ?**
* Si un garant perd son téléphone ou ne répond pas → il en reste 3, ça passe.
* Un garant seul (ou deux) **ne peut rien faire**. Même Swissquote seul ne peut pas prendre ton wallet.
* Un garant **ne voit pas** ton argent et **ne peut pas** l'envoyer : il peut seulement voter pour une nouvelle clé.

**Pourquoi un délai ?** Si 3 garants se font piéger (ou s'entendent contre toi), tu as encore ton téléphone :
SwissWallet affiche une alerte rouge dans *Profile → Guardians* → *Cancel this recovery*. Tu gagnes toujours.

## 4. La démo pas à pas (un seul émulateur)

1. Swissquote → Wallet → *Generate my wallet*.
2. SwissWallet → Receive → récupère un peu d'ETH de test (faucet Base Sepolia) → fais un premier Send
   (ça déploie le coffre).
3. Profile → **Guardians** : Swissquote est déjà là. Ajoute 3 garants (par ex. 2 simulés + le code de
   *Be a guardian* de l'app elle-même, ou 3 simulés) → **Activate protection (3 of 4)**.
   → une seule opération gasless : déploie le module (la toute première fois sur le réseau) + enregistre tes 4 garants.
4. Profile → **Simulate lost phone (demo)** → la passkey est supprimée (= téléphone perdu).
5. **I lost my phone — recover my wallet** → l'adresse est pré-remplie → *Find my guardians*.
6. *Create my new key & ask guardians* → une **nouvelle passkey** est créée.
7. *Ask Swissquote* (vérification d'identité) + *Approve with simulated guardians* → **3 / 3**.
8. *Start recovery* → compte à rebours **30 s** → *Finish recovery*.
9. Tu es de retour sur Home : **même adresse, même solde**, nouvelle passkey. L'ancienne est rejetée.

## 5. Ce qui se passe techniquement

| Étape | Qui | Appel on-chain |
|---|---|---|
| Création du wallet | SwissWallet | adresse = `factory.getAddress([passkey(x,y), module], 0)` (rien n'est déployé) |
| Activer la protection | ton wallet (UserOperation signée par la passkey, gas payé par Pimlico) | `executeBatch([ CREATE2 → déploie le module si besoin, module.setupGuardians(4 adresses, 3, 30) ])` |
| Un garant approuve | le garant (hors-chaîne) | signe `recoveryHash(wallet, nouvelle clé, index à retirer, nonce, chainId, module)` (EIP-191) |
| Démarrer la récupération | nouveau téléphone, via un petit wallet « helper » à lui | `module.initiateRecovery(wallet, x, y, index, [3 signatures])` → vérifie 3 garants différents → démarre le timer |
| Annuler (si c'est une attaque) | ancien téléphone | `module.cancelRecovery()` |
| Finir | nouveau téléphone (helper) | `module.finalizeRecovery(wallet)` → `wallet.addOwnerPublicKey(x, y)` + `removeOwnerAtIndex(ancienne passkey)` |

Sécurité du module :
* le **nonce** change à chaque démarrage / reconfiguration → une signature ne sert **qu'une fois** ;
* le **chainId** et l'adresse du module sont dans la signature → pas de rejeu sur une autre chaîne ;
* un même garant compté **une seule fois**, les non-garants sont ignorés ;
* le module ne retire **que des passkeys** (64 octets), jamais une adresse → il ne peut pas se bloquer lui-même.

Le module est **un seul contrat partagé** par tous les wallets, à la même adresse sur toutes les chaînes
(CREATE2) : `0xB3786e6BdB300575df7b5b3291E387a6f287428F`.

## 6. Fichiers

| Fichier | Rôle |
|---|---|
| `contracts/contracts/SwissRecoveryModule.sol` | le contrat (Solidity) |
| `contracts/test.js` | 10 tests du contrat |
| `contracts/e2e.cjs` | test de bout en bout avec le **vrai** Coinbase Smart Wallet + EntryPoint, et le **code Kotlin de l'app** |
| `core/recovery/Recovery.kt` | `RecoveryRequest` (ce que signent les garants), `GuardianApproval`, règle 3/4 + 30 s |
| `swisswallet/data/recovery/SocialRecoveryService.kt` | toute la logique : activer, annuler, demander, collecter, démarrer, finir |
| `swisswallet/data/recovery/GuardianRepositories.kt` | noms des garants, garants simulés, mode « Be a guardian » |
| `swisswallet/data/key/AndroidPasskeyVault.kt` | passkey P-256 dans l'Android Keystore |
| `swisswallet/ui/recovery/` | écrans Guardians, Recover my wallet, Be a guardian |
| `swissquote/guardian/GuardianApprovalActivity.kt` | Swissquote garant : vérification d'identité puis signature |

## 7. Ce qui est simulé (et ce qu'il faudrait en vrai)

* La **vérification d'identité Swissquote** est simulée → en vrai : KYC (selfie + pièce d'identité), 2FA.
* La **clé de garant Swissquote** est dans l'app → en vrai : dans un **HSM** sur les serveurs de Swissquote.
* Les **garants simulés** ont leur clé sur le même téléphone → en vrai, chaque garant a la sienne sur son appareil.
* Les codes (`SWREC1`, `SWSIG1`, `SWGRD1`) se partagent par copier/coller ou « Share » → en vrai : QR code ou notification push.
* Délai **30 s** → en vrai **48 h** (changer `RecoveryPolicy.DELAY_SECONDS`).
