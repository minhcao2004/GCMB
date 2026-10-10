<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Documentation and team handoff

- Before coding, read `README.md` and the relevant files in `docs/`; compare the requested work with the documented scope and behavior.
- Keep documentation in sync with implementation. If code adds or changes behavior, API contracts, database schema, configuration, or user flows that the existing docs do not describe, update the relevant documentation in the same task. If that cannot be done, explain why and list the undocumented changes in the final summary.
- In every coding task's final summary, include a **“Ngoài tài liệu hiện có”** section. List the new or changed behavior, affected files, and any follow-up the team should know about. If there is nothing beyond the docs, say so explicitly.
- Write that section so the user can forward it to the team. Do not send messages through external tools unless the user explicitly authorizes contacting the teammate and provides or identifies the destination.
