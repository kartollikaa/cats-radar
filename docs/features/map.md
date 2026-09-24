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
  tab groups them and drawn in its list layout, whichever layout the tab is set to. The outing
  backfill gives every cat of a walk the same fix, so this is common. A row opens its cat, back
  returns to the list, and a second back closes it. A tap that lands on several dots at once opens
  the same list. A list whose cats are all deleted closes, and restoring one of them does not reopen
  it.
- **The list is a sheet on the back stack**, its own screen above the map rather than a part of it.
  It lists the cats it was opened with, under the coat choice the map had then; the choice cannot
  change while it is open, because it is made on the map beneath. While a cat opened from the list
  is on top, the list is out of sight: the back gesture from the cat shows the map as the cat shrinks
  away, and the list slides back up once the gesture lands.

## An outing's route

"On the map" on an outing's header in the Encounters list switches to the Map tab showing that outing
alone. Only its cats are on the map, and the view fits around them. A line joins the located ones in
the order they were seen, with a chip naming the outing above them. Closing the chip, or pressing
back, returns to every cat and fits the view around them again. A spot's list offers the same action
for its outings. The line is drawn from cat to cat. It is not the route actually walked, which is
the walk tracks' job.

- **No located cat in an outing:** its header offers no map.
- **The outing changes while it is shown:** a cat added to it or deleted from it moves the line with
  it. If its last located cat is deleted, the map returns to every cat, and stays there even when
  another of its cats gets a location later.
- **Cats sharing one fix** give the line no length there; their cluster still opens as a spot's list.

## Heat and coats

Two chips sit at the map's top edge.

- **Heatmap** draws where cats are seen most, weighing every cat alike, and hides the dots while it
  is on. Its heat is drawn from the cats themselves, not from their clusters, so ten cats at one
  spot weigh ten times one.
- **Coats** opens the coat grid: choosing coats shows only cats of those coats, and "Not specified"
  shows the cats with none noted. The choice applies to the dots, the clusters, the heat, a
  focused outing and a spot's list alike; a focused outing's line still runs through all of its
  cats, since it is the order they were seen in. The view stays where it is when the choice changes, and "Every coat" clears
  it. A choice that matches no cat says so, rather than showing a map with nothing on it.

Both last as long as the tab does; leaving the tab clears them.

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
  on), `MapStateMapper`, `MapStore`; `MapSpotState`, `MapSpotStateMapper`, `MapSpotStore` — a
  spot's list
- `ui/…/map/MapScreen.kt` — the map and its style; `CatLayers.kt` — the dots, the clusters and
  their taps; `MapFeatures.kt` — cats as map features; `MapSpotScreen.kt` — a spot's list, drawn by
  the Encounters tab's own `EncounterRows` in its list layout; `MapOverlay.kt` — the chips over the map;
  `MapCoatSheet.kt` — the coat choice
- `app/…/navigation/CatsMap.kt`, `Destinations.kt` — the tab; `MapSpot.kt` — a spot's list on the
  back stack, drawn as a sheet by `BottomSheetSceneStrategy.kt`; `MapFocusRequest.kt` — the outing
  another tab, or a spot's list, asked the map to show

## Not built yet

Walk tracks are the next slices of the Map epic (`docs/tbd/decompositions/2026-09-23-map-epic.md`).
