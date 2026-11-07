# RotinaCheck

[![Android CI](https://github.com/Chrispsz/rotina-check/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Chrispsz/rotina-check/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/Chrispsz/rotina-check?label=Release)](../../releases/latest)
[![License: MIT](https://img.shields.io/badge/Licen%C3%A7a-MIT-blue.svg)](LICENSE)

Checklist de rotina diária organizada por blocos de tempo. Importe sua rotina em `.json`, marque as tarefas do dia e receba alertas discretos nos horários dos blocos.

**100% local · zero telemetria · zero contas · tema AMOLED puro**

---

## ✨ O que faz

- **Blocos retráteis com checklist** — horários e tarefas com marcação rápida
- **Progresso diário** — barra percentual atrelada às marcações
- **Consistência (7 dias)** — "X/7 dias concluídos" em mini-indicadores, com reset
- **Dia de semana ↔ Fim de semana** — alternador rápido (detecta o dia automaticamente)
- **Editor em tempo real** — ajuste blocos, horários e tarefas direto no app, sem recompilar
- **Alertas discretos** — aviso no início e no fim de cada bloco, via alarmes exatos do sistema (zero bateria em standby)
- **Importar / Exportar / Limpar** — rotina em `.json` e backup completo, tudo por arquivo local

## 🚀 Como usar

1. Instale o APK da [última release](../../releases/latest)
2. Abra **Configurações → Importar rotina (.json)** — crie o seu arquivo no formato abaixo e importe
3. Marque as tarefas ao longo do dia
4. (Opcional) Ative os alertas e defina o link do seu Pomodoro no topo da tela

> Sua rotina é sua: este repositório não inclui nenhuma rotina de exemplo com dados reais — apenas o formato.

### Formato da rotina

```json
{
  "versao": "1.0",
  "diasSemana": [
    {
      "id": "semana",
      "nome": "Segunda a Sexta",
      "blocos": [
        {
          "id": "b1",
          "nome": "Manhã",
          "horario": "06:00 - 12:00",
          "tarefas": ["Tarefa 1", "Tarefa 2"]
        }
      ]
    },
    { "id": "fim-de-semana", "nome": "Sábado e Domingo", "blocos": [ ... ] }
  ]
}
```

- `horario` aceita `"HH:mm - HH:mm"`, `"HH:mm em diante"` ou vazio (bloco sem horário)
- O id `fim-de-semana` (ou nome com "sábado"/"domingo") é usado nos fins de semana
- O backup exportado inclui rotina + configurações + histórico e pode ser reimportado

## 🔒 Privacidade

- Todo dado vive no aparelho (DataStore local) — nada sai dele
- Permissões: notificações, alarmes exatos e reinicialização (apenas para re-agendar os avisos)
- Sem rede, sem analytics, sem login

## 🧱 Engenharia

| Aspecto | Decisão |
|---|---|
| Stack | Kotlin 2.2 · Compose (BOM 2025.10) · Material 3 · minSdk 34 / target 36 |
| Persistência | DataStore Preferences — a única camada de storage |
| Dependências | Sem Hilt/Room/KSP/WorkManager — APK mínimo, build rápido |
| Bateria | Modelo passivo: **exatamente 1 alarme exato registrado por vez**, re-armado em cadeia; nada residente entre blocos |
| Testes | JUnit 5: parser, horários, progresso, consistência, editor, agendamento e backup (round-trip) |
| CI | Lint → testes → APK de release minificado; tag `v*` publica a release |

AMOLED: fundo `#000000` real (janela, tema e ícone) — pixels desligados em telas OLED.

## 🛠️ Build

```bash
./gradlew :app:assembleRelease   # APK minificado
./gradlew :app:testDebugUnitTest # suíte de testes
```

## 📄 Licença

[MIT](LICENSE)
