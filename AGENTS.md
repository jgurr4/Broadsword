## Agent skills

### Issue tracker

Issues live in a self-hosted Forgejo at http://localhost:3001 (REST API via `curl` + access token). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Version Control

Never commit any change to the repo without human approval first. Have a commit message prepared for the work that was accomplished, and explain   how to close any tickets in case the user decides to manually do that.

### Wiki Ingest

Always have a `progress.md` file in the project (not in the `raw` or `wiki` folder) which explains the status of ingested raw files and how much   is left to be done. Update this every time you ingest more raw files into the wiki using the wiki-ingest skill

