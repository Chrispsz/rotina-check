# 📡 OTA inteligente via GitHub Releases

> Como o RotinaCheck se atualiza sozinho — sem loja, sem incômodo e quase sem
> usar rede, bateria ou dados. E como reutilizar isso em **qualquer** app seu
> publicado no GitHub.

## Filosofia

Um updater não pode ser chato. Muitos apps pecam: checam toda hora, abrem diálogo bloqueante, piscam badge, insistem. O objetivo aqui é o oposto — **discreto, econômico e persistente na medida certa**:

1. **Discreto** — um cartãozinho silencioso no topo. Sem diálogo, som, vibração ou badge vermelho.
2. **Econômico** — no máximo **1 requisição por dia** (~2–5 KB), apenas com o app aberto em primeiro plano. Zero em segundo plano.
3. **Persistente na medida certa** — achou atualização? O aviso **continua** aparecendo a cada abertura (offline, sem gastar rede) até você atualizar — ou ignorar aquela versão.

## As regras inteligentes

| Regra | Como funciona |
|---|---|
| ⏱️ Máx. 1 verificação/dia | Portão diário persistido em DataStore; rotações/reaberturas no mesmo dia = leitura local, sem rede |
| 📶 Econômico com dados móveis | Em rede medida, só verifica de novo após 3 dias — o Wi‑Fi faz o trabalho diário |
| 🔕 Falha silenciosa | Sem internet/timeout/erro = nada acontece; amanhã tenta de novo (e a tentativa também conta como "checado hoje") |
| 📴 Offline-first | A atualização encontrada fica salva; o banner reaparece a cada abertura **sem rede nenhuma** |
| 👆 Atualizar em 1 toque | Download pelo `DownloadManager` do sistema (notificação nativa, sobrevive ao fechamento do app) e instalação direta via instalador do Android — sem navegador e sem novas permissões |
| 🙈 Ignorar esta versão | A versão ignorada nunca mais avisa; uma versão **mais nova** sempre avisa |
| ✨ Auto-limpeza pós-atualização | Instalou a nova versão? O estado pendente é limpo comparando versões localmente — sem rede |
| 🧪 Canal estável | `releases/latest` ignora drafts e prereleases — tags `v1.2.3-test` nunca disparam o banner |

## Fluxo

```text
app abre (ON_START)
   │
   ├─ resolve estado pendente OFFLINE (DataStore) → mostra/limpa banner se preciso
   │
   ├─ gate: já checou hoje? ── sim ─► fim (custo: 1 leitura de DataStore)
   │         │ não (e Wi‑Fi, ou ≥3 dias em dados móveis)
   ▼
   1 GET https://api.github.com/repos/Chrispsz/rotina-check/releases/latest
   │
   ├─ falhou? ─► silêncio; marca "checado hoje"; fim
   ├─ versão ≤ instalada? ─► limpa pendente; fim
   └─ versão > instalada? ─► salva pendente (JSON ~300 bytes) ─► banner discreto
                                 │
                                 ├─ ✕ → esconde só nesta sessão (volta na próxima abertura)
                                 ├─ Ignorar esta versão → nunca mais avisa (até versão nova)
                                 └─ Atualizar (1 toque) ─► DownloadManager do sistema
                                        ├─ baixando → barra de progresso no próprio banner
                                        │    (continua mesmo se fechar o app; ao reabrir,
                                        │     o estado é reconciliado com o DownloadManager)
                                        └─ pronto → "Instalar agora" ─► instalador do Android
                                             (FileProvider; sem navegador, sem procurar arquivo)
```

## Custo real

- **Rede:** 1 GET/dia, resposta ~2–5 KB (JSON da release). Em dados móveis: no máx. 1 GET a cada 3 dias. O download do APK (~2,5 MB) só acontece quando VOCÊ toca em "Atualizar".
- **Bateria:** zero em segundo plano. Sem WorkManager, sem AlarmManager, sem push, sem serviço. A verificação só acontece com o app aberto; o download fica por conta do `DownloadManager` do sistema.
- **Armazenamento:** 5 chaves no DataStore (dia da última checagem, versão ignorada, JSON pendente, id e versão do download em andamento) + o APK baixado no diretório privado do app, apagado sozinho após a atualização.

## Arquitetura

| Arquivo | Papel |
|---|---|
| `update/GitHubSmartUpdate.kt` | **Núcleo completo em 1 arquivo** — semver (com regra de prerelease), fetch HTTP (`HttpURLConnection`, zero dependência nova), parser `org.json`, persistência DataStore, máquina de estados (`Idle / Checking / Available / Downloading / ReadyToInstall`), gate diário, download+instalação via `DownloadManager`/FileProvider |
| `viewmodel/UpdateViewModel.kt` | Ponte para a UI; único lugar com `owner`/`repo` configurados |
| `ui/components/UpdateBanner.kt` | Banner discreto do Home (entra/sai animado, ✕ por sessão, toque p/ expandir e ignorar, progresso do download, botão "Instalar agora") |
| `MainActivity.kt` | Gatilho: `ON_START` → `update.onAppOpened()` |
| `SettingsScreen.kt` | Card "Atualizações": versão instalada, "Verificar agora" (bypass do gate), baixar, ignorar |

### Referência técnica (GitHub)

- Endpoint: `GET /repos/{owner}/{repo}/releases/latest` — já exclui drafts e prereleases
- Headers: `User-Agent` é **obrigatório** (sem ele a API devolve 403) e `Accept: application/vnd.github+json`
- Rate limit sem token: 60 req/h por IP — irrelevante para 1 req/dia
- Campos usados: `tag_name` (semver), `body` (prévia das notas, 300 caracteres), `html_url`, `assets[].browser_download_url` (primeiro `*.apk`)
- Timeout: 8 s; qualquer status ≠ 200 é tratado como "não deu hoje"

### Publicando uma atualização

```bash
# 1) suba versionName/versionCode no app/build.gradle.kts
# 2) commit + tag semver
git tag v0.4.0
git push origin main v0.4.0
# 3) a CI anexa RotinaCheck-v0.4.0.apk (+ sha256) na release
```

Quem tem v0.3.x instalado recebe o banner no dia seguinte — ou na hora, pelo "Verificar agora" nas Configurações.

> **Canal de teste:** publique a tag como **prerelease** (ex.: `v0.5.0-test`; a CI marca automaticamente tags com `-test` como prerelease) — o OTA ignora prereleases.

## Reusar em qualquer app seu no GitHub

O núcleo é **um único arquivo Kotlin sem dependências novas** (apenas APIs de plataforma: `HttpURLConnection`, `org.json`, DataStore Preferences).

1. **Copie** `update/GitHubSmartUpdate.kt` para o outro app (ajuste o `package`)
2. **FileProvider**: copie `res/xml/file_paths.xml` e adicione o provider no Manifest (autoridade `${applicationId}.updateprovider` — veja o `AndroidManifest.xml` deste repo; **não** é preciso `REQUEST_INSTALL_PACKAGES`)
3. **Configure** no ViewModel: instancie com `owner`, `repo`, a versão instalada (`PackageManager.PackageInfoFlags`) e um `CoroutineScope`
4. **Gatilho**: chame `updater.onAppOpened()` a cada `ON_START` do app
5. **UI**: observe `state` e renderize seu banner (ou copie o `UpdateBanner.kt`) — estados `Downloading` (progresso) e `ReadyToInstall` ("Instalar agora") já vêm prontos

Pronto — qualquer app Android com releases no GitHub passa a ter o mesmo OTA econômico. O `UpdateViewModel.kt` deste repositório é um exemplo completo de integração.

## FAQ

**Preciso da Play Store?** Não. "Atualizar" baixa o APK da release pelo `DownloadManager` e o "Instalar agora" abre o instalador do Android — na primeira vez o sistema pede autorização de "instalar apps desconhecidos" para o RotinaCheck (uma vez só).

**E se eu nunca atualizar?** Nada quebra: o aviso continua discreto e 1 req/dia é desprezível. "Ignorar esta versão" existe exatamente para isso.

**Modo avião / offline?** A checagem falha em silêncio; o estado pendente (se houver) continua sendo resolvido offline.

**"Verificar agora" gasta mais rede?** Só quando VOCÊ pede — é o bypass manual do gate diário, dentro de Configurações.
