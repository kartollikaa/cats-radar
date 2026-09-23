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
so the tile server learns which area is on screen and from which address, as with any web map, and
because the map opens fitted around your cats, the first area it asks for is the one around where
you have seen them. No cat is sent: the dots are drawn on the phone from the phone's own database.
Tiles already seen, and the style, are kept in the app's cache, which no backup includes.

## Which cats are on it

Every cat that is not deleted and has coordinates, from whichever source they came: a GPS fix, the
last known position, a photo's own EXIF. A cat still waiting for a location, or one that never got
one, is not drawn, and the list keeps it as before. Neither is a cat whose coordinates are off the
globe, past a pole or the 180th meridian.

## Tapping the map

- **A dot** opens its cat's detail above the Map tab; back returns to the map as it was left.
- **Dots close together** at the current zoom draw as one circle holding their count. Tapping it
  zooms in until they come apart.
- **Cats that never come apart** open as a list of that spot, grouped by outing as the Encounters
  tab groups them. The outing backfill gives every cat of a walk the same fix, so this is common.
  A row opens its cat, back returns to the list, and a second back closes it. A tap that lands on
  several dots at once opens the same list.

## At the edges

- **No cat has a location yet:** the tab says so instead of showing an empty map, and so loads no
  tiles until there is something to show.
- **A cat gets its location while the map is open:** its dot appears where it is, and the view stays
  where it was panned rather than jumping to fit it. Rotating the phone or switching the theme keeps
  that view too; only leaving the tab and coming back fits the map around the cats again.
- **No connection:** the dots sit on the map's style, so without the style there is nothing to draw
  them on. An area already seen loads from the cache; a first look with no connection says the map
  could not load, rather than showing an empty canvas.
- **TalkBack** hears how many cats the map shows; the dots themselves are not reachable yet, and a
  spot's list is read like the Encounters tab.
- **Cats on both sides of the 180th meridian**, in Fiji or Chukotka, open on a view spanning the
  world: the fitted area runs west to east the long way round. Every dot is still on screen.
- **White cats on a light street:** each dot has the same outline the coat faces carry, so a white
  cat's dot does not disappear into the map.

## Where the code lives

- `presentation/…/map/` — `MapState` (loading, empty, or the located points and the area to open
  on), `MapStateMapper`, `MapStore`
- `ui/…/map/MapScreen.kt` — the map and its style; `CatLayers.kt` — the dots, the clusters and
  their taps; `MapFeatures.kt` — cats as map features; `MapSpotSheet.kt` — a spot's list, drawn by
  the Encounters tab's own `EncounterList`
- `app/…/navigation/CatsMap.kt`, `Destinations.kt` — the tab

## Not built yet

An outing's route, walk tracks and a heatmap are the next slices of the Map epic
(`docs/tbd/decompositions/2026-09-23-map-epic.md`).
