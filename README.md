# Item Search

A RuneLite plugin: search NPCs and items, view loot tables, purchase sources,
and personal drop-rate statistics (kills to obtain, dry streaks, respawn
tracking, spawn map markers).

The bundled data (`src/main/resources/com/itemsearch/*.json`) is generated
offline by the ItemSearchDataGenerator project (E:\Projects\Python\ItemSearchDataGenerator)
from the OSRS Wiki and pinned open databases.

## Development

- `./gradlew test` — run the unit tests
- `./gradlew run` — launch a development-mode RuneLite client with the plugin
  loaded (see https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
  for logging in)
