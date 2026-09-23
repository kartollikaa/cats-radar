# Map

The **Map** tab shows every cat that has a location as a dot on a map, coloured by its coat: a ginger
cat is a ginger dot, a cat with no coat noted takes the theme's teal. The map opens fitted around all
of them, and a single cat, or a handful on one street, opens on a street-sized area rather than a
doorstep.

## Where the map comes from

The map is [MapLibre](https://maplibre.org/) drawing vector tiles from
[OpenFreeMap](https://openfreemap.org/), which serves OpenStreetMap data with no key, account or
cookie. Its light style follows the light theme and its dark style the dark one. The attribution
OpenFreeMap and OpenStreetMap require stays on the map, in the corner the library draws it in.

**This is the one screen that goes online.** Until the map, the app declared no `INTERNET`
permission at all: geocoding runs through the platform. Tiles are fetched as the map is looked at,
so the tile server learns which area is on screen, as with any web map. Nothing about the cats
themselves is sent: the dots are drawn on the phone from the phone's own database.

## Which cats are on it

Every cat that is not deleted and has coordinates, from whichever source they came: a GPS fix, the
last known position, a photo's own EXIF. A cat still waiting for a location, or one that never got
one, is not drawn, and the list keeps it as before.

## At the edges

- **No cat has a location yet:** the tab says so instead of showing an empty map, and so loads no
  tiles until there is something to show.
- **A cat gets its location while the map is open:** its dot appears where it is, and the view stays
  where it was panned rather than jumping to fit it.
- **White cats on a light street:** each dot has the same outline the coat faces carry, so a white
  cat's dot does not disappear into the map.
- **16 KB pages:** the renderer brings native libraries of its own. They were checked on the 16 KB
  emulator the way `docs/reference/16kb-page-size.md` describes, and the platform raised no
  warning.

## Where the code lives

- `presentation/…/map/` — `MapState` (loading, empty, or the located points and the area to open
  on), `MapStateMapper`, `MapStore`
- `ui/…/map/MapScreen.kt` — the map, its style and the dots
- `app/…/navigation/CatsMap.kt`, `Destinations.kt` — the tab

## Not built yet

Tapping a dot, clustering, an outing's route, walk tracks and a heatmap are the next slices of the
Map epic (`docs/tbd/decompositions/2026-09-23-map-epic.md`).
