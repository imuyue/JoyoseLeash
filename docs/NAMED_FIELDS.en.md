# Named fields in lists

[简体中文](NAMED_FIELDS.md) | English

Named fields are stored as single-key objects. The name is a JSON key, not a display alias. For example:

```json
[{"enabled": false}]
```

## Creating and renaming

- Object: select “Add”, enter a name, then select a type.
- List: select “Named field (saved as object)”, enter a name, then select a type.
- Existing ordinary list item: `⋯ → Make named field → Enter name → Convert`.
- Named field: `⋯ → Rename`. The original value, type, and position are retained.

“Ordinary list item (unnamed)” remains available. Existing data is not converted automatically. Names cannot be empty, and an existing key in the same object cannot be overwritten.

## Editing behavior

- A switch changes only the inner boolean, not the enclosing object.
- Reordering moves the entire list item, including its name and value.
- “Expand object” opens the wrapper object and allows additional fields.
- Reselecting the current type does not change data. Changing types requires confirmation before clearing the old value.
- The local JSON draft focuses the corresponding field after creation, conversion, or renaming.

## Structural limits

Converting `[false]` to `[{"enabled":false}]` changes the list item’s type. The target rule consumer must support that structure. Conversion requires confirmation; canceling changes no data, and conversion does not apply rules automatically.

The system-cloud entry saves only local drafts. It does not write system databases or caches.
