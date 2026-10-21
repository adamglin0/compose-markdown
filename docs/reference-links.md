# Reference Links

This stage adds reference-style link resolution without breaking the append-only engine model.

## What Is Indexed

- `label -> definition`
- `unresolved label -> dependent top-level block IDs`

Definitions are normalized case-insensitively with collapsed internal whitespace.

## Resolution Flow

1. block parsing collects link reference definitions (which may span several lines) from the start of paragraphs and keeps them out of the public block list
2. inline parsing resolves reference-style links when the active preset enables them
3. unresolved labels are recorded against the current top-level block
4. when a later append introduces or changes a definition, only the blocks that use its label are rebuilt, keeping their ids

## Current Supported Forms

- `[text][label]`
- `[text][]`
- shortcut `[label]`
- image equivalents when image parsing is enabled

## Current Limits

- raw HTML stays literal text and is unrelated to link-definition handling
- text that is not a valid definition stays paragraph text
