Verbatim copies of admoai/adhub `shared/interactiveexperience/contract/fixtures/decision/`
(contract pack, admoai/adhub#7127, commit 5566d97e2).
Never edit by hand; re-copy when the pack changes.

`bridge/` is a verbatim copy of admoai/adhub `shared/interactiveexperience/contract/fixtures/bridge/`.
Every fixture that is on adhub main comes from main (#7162, commit 2cd04817c). The fixtures the
handshake follow-up adds (bridge-ready's `bridgeVersion`, submitting, submit-failed, back and the
invalid back-*, oversized-message, unknown-bridge-version and wrong-channel) come from admoai/adhub
PR #7170 (commit 52eb9598a); re-copy them from main when it merges. Never edit by hand.
`context.json` is the identity the SDK minted for one controller: `instanceId` is the descriptor's
`formInstanceId`; `channel` is random per controller and reaches the page only in the launch URL's fragment.
