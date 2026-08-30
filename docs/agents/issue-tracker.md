# Issue tracker: Forgejo (self-hosted)

Issues and specs for this repo live in a self-hosted Forgejo instance at
`http://localhost:3001`, repo `jmgurr/Broadsword`. Forgejo's API is
Gitea-compatible; no CLI is installed, so use the REST API with `curl`.

## Setup

- **Base URL**: `http://localhost:3001`
- **Auth**: access token in the `FORGEJO_TOKEN` env var, sent as
  `Authorization: token $FORGEJO_TOKEN` (create one in Forgejo:
  Settings → Applications → New token).
- **Repo slug**: `jmgurr/Broadsword`

Helper for all calls:

```bash
API="http://localhost:3001/api/v1/repos/jmgurr/Broadsword"
H=(-H "Authorization: token $FORGEJO_TOKEN" -H "Content-Type: application/json")
```

## Conventions

- **Create an issue**: `curl -s -X POST "${H[@]}" -d '{"title":"...","body":"..."}' "$API/issues"`
- **Read an issue**: `curl -s "${H[@]}" "$API/issues/<number>"` and `curl -s "${H[@]}" "$API/issues/<number>/comments"`
- **List issues**: `curl -s "${H[@]}" "$API/issues?state=open"` (add `&labels=<name>` to filter)
- **Comment**: `curl -s -X POST "${H[@]}" -d '{"body":"..."}' "$API/issues/<number>/comments"`
- **Labels**: ensure the label exists first — `curl -s -X POST "${H[@]}" -d '{"name":"needs-triage","color":"bbbbbb"}' "$API/labels"` — then add/remove with `POST` / `DELETE` on `$API/issues/<number>/labels/<label-name>`
- **Close**: `curl -s -X PATCH "${H[@]}" -d '{"state":"closed"}' "$API/issues/<number>"`

For multi-line bodies, build the JSON with a heredoc and `jq -n --arg body "$(cat)" '{...}'` rather than hand-escaping quotes.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

If set to `yes`, use the `/pulls` equivalents of `/issues` above. There is no author-association field; filter by comparing the PR author against the repo owner.

## When a skill says "publish to the issue tracker"

Create a Forgejo issue via the API.

## When a skill says "fetch the relevant ticket"

`curl -s "${H[@]}" "$API/issues/<number>"` plus its `/comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: an issue labelled `wayfinder:map` holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: an issue with `Part of #<map>` on the first line of its body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev (PATCH the issue with `assignee_ids`).
- **Blocking**: a `Blocked by: #<n>, #<n>` line at the top of the child body (Forgejo has no native issue dependencies). A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children, drop any with an open `Blocked by` entry or an assignee; first in map order wins.
- **Claim**: assign the child issue to the session's user, the session's first write.
- **Resolve**: comment the answer on the child, close it, append a context pointer (gist + link) to the map's Decisions-so-far.
