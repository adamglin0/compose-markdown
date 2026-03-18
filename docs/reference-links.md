# Reference Links

This stage adds reference-style link resolution without breaking the append-only engine model.

## What Is Indexed

- `label -> definition`
- `unresolved label -> dependent top-level block IDs`

Definitions are normalized case-insensitively with collapsed internal whitespace.

## Resolution Flow

1. block parsing collects one-line link reference definitions and keeps them out of the public block list
2. inline parsing resolves reference-style links when the active preset enables them
3. unresolved labels are recorded against the current top-level block
4. when a later append introduces a new definition, only the dependent preserved blocks are reparsed

## Current Supported Forms

- `[text][label]`
- `[text][]`
- shortcut `[label]`
- image equivalents when image parsing is enabled

## Current Limits

- raw HTML remains disabled and unrelated to link-definition handling
- multi-line reference definitions are not implemented in this stage
- unsupported definition forms degrade to plain text instead of partial parsing
