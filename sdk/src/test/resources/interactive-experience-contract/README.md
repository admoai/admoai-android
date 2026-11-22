Verbatim copies of admoai/adhub `shared/interactiveexperience/contract/fixtures/decision/`
(contract pack, admoai/adhub#7127, commit 5566d97e2).
Never edit by hand; re-copy when the pack changes.

`bridge/` is a verbatim copy of admoai/adhub main `shared/interactiveexperience/contract/fixtures/bridge/`
(#7162, #7170 and the per-entry prefill rule #7299, synced at ce283cefb). `tolerated/` holds messages
the page accepts after dropping entries, each with its `.outcome.json`; the SDK never sends them. Never edit by hand; re-copy it
from main when the pack changes.
`context.json` is the identity the SDK minted for one controller: `instanceId` is the descriptor's
`formInstanceId`; `channel` is random per controller and reaches the page only in the launch URL's fragment.
