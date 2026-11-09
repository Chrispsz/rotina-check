# RotinaCheck

[![Android CI](https://github.com/Chrispsz/rotina-check/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Chrispsz/rotina-check/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/Chrispsz/rotina-check?label=Release)](../../releases/latest)
[![License: MIT](https://img.shields.io/badge/Licen%C3%A7a-MIT-blue.svg)](LICENSE)

Checklist de rotina diária organizada por blocos de tempo, **um dia de cada vez** — cada dia da semana tem seus próprios blocos. Já vem com um exemplo genérico pronto para editar, marcação de tarefas e alertas discretos nos horários dos blocos.

**100% local · zero telemetria · zero contas · tema AMOLED puro**

> 🔒 **Privacidade:** nenhum dado pessoal sai do aparelho. O exemplo embutido no app é 100% genérico (e guardado por um teste de regressão) — rotinas de verdade vivem apenas no seu dispositivo.

---

## ✨ O que faz

- **Rotina organizada por dia** — chips Seg–Dom no topo; cada dia tem seus próprios blocos (a manutenção de terça fica em terça, a de sexta em sexta)
- **Já vem com exemplo genérico** — abre editável no primeiro uso: renomeie blocos, mude horários, apague o que não serve
- **Criação manual de blocos** — botão **+** cria um bloco no dia que está sendo visto
- **Card "Agora"** — mostra o bloco em curso com contagem regressiva (ou o próximo), sem rolar a tela
- **Blocos retráteis com checklist** — o bloco do momento já abre expandido para marcar num toque
- **Outros dias = visualização** — ao tocar num chip de outro dia, os blocos aparecem sem marcação (só hoje aceita marcações)
- **Progresso diário + consistência (7 dias)** — barra percentual e mini-indicadores, com reset
- **Editor em tempo real** — ajuste blocos, horários e tarefas direto no app, salva sozinho
- **Alertas discretos** — aviso no início e no fim de cada bloco, via alarmes exatos do sistema (zero bateria em standby)
- **Importar / Exportar / Limpar** — rotina em `.json` e backup completo, tudo por arquivo local

## 🚀 Como usar

1. Instale o APK da [última release](../../releases/latest)
2. O app já abre com um **exemplo genérico** — edite os blocos (ícone ✏️), crie os seus (**+**) ou importe sua rotina:
   - **Configurações → Importar rotina (.json)**
   - Apagar tudo abre a tela inicial com **Usar exemplo / Criar do zero / Importar .json**
3. Marque as tarefas ao longo do dia (chips Seg–Dom para ver os outros dias)
4. (Opcional) Ative os alertas em **Configurações**

### Formato da rotina

```json
{
  "versao": "1.0",
  "diasSemana": [
    {
      "id": "seg",
      "nome": "Segunda",
      "dias": [1],
      "blocos": [
        {
          "id": "seg_b1",
          "nome": "Manhã",
          "horario": "06:00 - 12:00",
          "tarefas": ["Café da manhã", "Estudo ou trabalho"]
        }
      ]
    },
    { "id": "sab", "nome": "Sábado", "dias": [6], "blocos": [ ... ] }
  ]
}
```

- `"dias": [1..7]` (opcional) — a quais dias da semana o bloco se aplica: **1 = segunda … 7 = domingo**. Com esse campo, cada dia da semana tem rotina própria
- Sem o campo `dias`, vale o formato antigo: um dia para segunda a sexta e outro para o fim de semana (heurística por id/nome)
- `horario` aceita `"HH:mm - HH:mm"`, `"HH:mm em diante"` ou vazio (bloco sem horário)
- O backup exportado inclui rotina + configurações + histórico e pode ser reimportado

## 🔒 Privacidade

- Todo dado vive no aparelho (DataStore local) — nada sai dele
- A ÚNICA rede que o app toca é a verificação de atualização: no máximo 1 requisição por dia, direto ao GitHub Releases (detalhes em [docs/OTA-GitHub.md](docs/OTA-GitHub.md))
- Permissões: notificações, alarmes exatos, reinicialização (re-agendar avisos) e INTERNET (só para a verificação de atualização acima)
- Sem analytics, sem login, sem rastreamento

## 🔄 Atualizações (OTA)

Atualizar sem entrar no GitHub: o app verifica a release mais recente **no máximo 1× por dia** (a cada 3 dias em dados móveis) e **só com o app aberto** — nada de segundo plano, push ou bateria.

- Encontrou versão nova? Um banner discreto aparece e **continua lá** (resolvido offline, sem nova rede) até você atualizar ou tocar em "Ignorar esta versão"
- **Atualizar em 1 toque**: download pelo sistema (com progresso, continua mesmo se fechar o app) → **"Instalar agora"** abre o instalador do Android — sem navegador, sem repositório, sem permissões novas
- Tags `v1.2.3-test` viram *prerelease* e **não** disparam o aviso (canal estável por padrão)
- Falha de rede? Silêncio — no dia seguinte tenta de novo

Arquitetura completa + guia de reuso para qualquer app: **[docs/OTA-GitHub.md](docs/OTA-GitHub.md)**

## 🧱 Engenharia

| Aspecto | Decisão |
|---|---|
| Stack | Kotlin 2.2 · Compose (BOM 2025.10) · Material 3 · minSdk 34 / target 36 |
| Persistência | DataStore Preferences — a única camada de storage |
| Dependências | Sem Hilt/Room/KSP/WorkManager — APK mínimo, build rápido |
| Bateria | Modelo passivo: **exatamente 1 alarme exato registrado por vez**, re-armado em cadeia; nada residente entre blocos |
| Testes | JUnit 5: parser, horários, progresso, consistência, editor, agendamento e backup (round-trip) |
| CI | Lint → testes → APK de release minificado; tag `v*` publica a release |
| Rede | Apenas verificação de atualização — 1 req/dia em foreground, silenciosa e offline-first |

AMOLED: fundo `#000000` real (janela, tema e ícone) — pixels desligados em telas OLED.

## 🛠️ Build

```bash
./gradlew :app:assembleRelease   # APK minificado
./gradlew :app:testDebugUnitTest # suíte de testes
```

## 📄 Licença

[MIT](LICENSE)
