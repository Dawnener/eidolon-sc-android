## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Implementing

实现票（`/implement`、`/implement-spec`，连同它们派的子代理）前读 `docs/agents/implementing.md`：本机跑哪些测试、draft PR 和 CI、怎么排票、每张票怎么收尾。

### Local acceptance

票里要求「本地构建版上验收」时，先读 `docs/agents/local-acceptance.md`：模拟器怎么起、开发和安装，服务器地址，假麦克风，怎么用后端状态造出各种结束卡。
