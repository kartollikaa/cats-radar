# Map

The **Map** tab shows every cat that has a location as a dot on a map, in its coat's colours: a ginger
cat is a ginger dot, a black-and-white one is black over white, a cat with no coat noted is a blue
dot. The map opens fitted around all of them, and a single cat, or a handful on one street, opens
on a street-sized area rather than a doorstep.

## How a dot shows its coat

The fur fills the top half of the dot and the markings share the bottom half, each a slice of it:

- a solid coat is one colour all over;
- an **"& white"** coat is its colour over white;
- **Calico, mostly white** is white over a ginger slice and a black one; **Calico, little white** is
  ginger over a white slice and a black one.

The dots are the colours of the coat faces (see [coat.md](./coat.md)), without the faces' tabby
stripes, which a dot is too small to carry: a brown dot and a black one differ by shade alone.
Clusters keep the theme's primary colour, whatever coats they hold.

## Where the map comes from

The map is [MapLibre](https://maplibre.org/) drawing vector tiles from
[OpenFreeMap](https://openfreemap.org/), which serves OpenStreetMap data with no key, account or
cookie. Its light style follows the light theme and its dark style the dark one. The attribution
OpenFreeMap and OpenStreetMap require stays on the map, in the corner the library draws it in.

**Tiles are the only screen content fetched from the network**, here and in the small map a cat's
detail draws around it ([encounter-detail.md](./encounter-detail.md#its-map)). Until the map, the app
declared no `INTERNET` permission at all: geocoding runs through the platform. Crash reports and screen
views go to Firebase as well (`analytics.md`), but no screen waits on them. Tiles are fetched as a map is
looked at, so the tile server learns which area is on screen and from which address, as with any web
map. Because the Map tab opens fitted around your cats, the first area it asks for is the one around
where you have seen them, and a cat's detail asks for the few streets around that one cat. No cat is
sent: the dots and the pin are drawn on the phone from the phone's own database. Tiles already seen,
and the style, are kept in the app's cache, which no backup includes.

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
  away, and the list slides back up once the gesture lands. Like every sheet in the app, the coat
  grid's included, it opens all the way and has no half-open stop (see [app-shell.md](./app-shell.md)).

## An outing's route

"On the map" on an outing's header in the Encounters list switches to the Map tab showing that outing
alone. Only its cats are on the map, and the view fits around them and their route. A chip names the
outing above the map; closing it, or pressing back, returns to every cat and fits the view around
them again. A spot's list and the cats at the bottom of Places offer the same action for their
outings.

The route is the recorded track of every walk that overlaps the outing's time span, drawn whole and
oldest first — the way to the first cat is part of the walk, not only the stretch between cats. A
line joining the located cats in the order they were seen stands in when no such walk has a track of
two points or more. The coat filter never thins either kind of line.

The map reads walks only while an outing is focused, and then only the tracks of the walks that
overlap it — never every point of every walk. With no outing focused it reads no walk at all.

- **No located cat in an outing:** its header offers no map.
- **The outing changes while it is shown:** a cat added to it or deleted from it moves the line with
  it, and a cat that stretches it into another walk's time brings that walk's track in. If its last
  located cat is deleted, the map returns to every cat, and stays there even when another of its cats
  gets a location later.
- **Cats sharing one fix** give the cat-to-cat line no length there; their cluster still opens as a
  spot's list.
- **A walk still recording** grows on the map as its points arrive; the view does not refit to it — it
  fits once, when the outing is focused.

## A cat's coordinates

A tap on the coordinates in a cat's detail ([encounter-detail.md](./encounter-detail.md)) switches to
the Map tab with the view on that cat: a street-sized area centred on its dot, the size a lone cat
opens on. Every other cat stays on the map around it.

- **From the Map tab itself**, when a dot or a spot's list opened the cat, the map comes back showing
  every cat: a focused outing, a coat choice and the heat are all let go, since each of them could
  hide the dot the tap asked for. The same map every other tab gets.
- **The view moves there once.** Afterwards a cat getting its location, or an outing focused and
  closed again, leaves the view where it is; tapping the coordinates again brings it back to the cat.
  A pan during the move stops it where it is, and that ends the request too.
- **A cat inside a cluster** stays inside it: the view centres on the cluster, and nothing marks which
  of its dots was asked for.
- **A cat no longer on the map** by the time the map opens, deleted in between, does not move the
  view: the map still comes back showing every cat, and its view stays where it was, or fits around
  every cat on a first look.

## Heat and coats

Two chips sit at the map's top edge.

- **Heatmap** draws where cats are seen most, weighing every cat alike, and hides the dots while it
  is on. Its heat is drawn from the cats themselves, not from their clusters, so ten cats at one
  spot weigh ten times one. The heat is in the cats' own fur colours: a street of ginger cats glows
  ginger, one of white cats glows white, one of black cats glows black. A cat gives its heat in the
  same shares as its dot, so a black-and-white cat is half black heat and half white. Each colour
  is its own layer, nearly opaque so the spots stay bright, and lighter furs lie over darker ones:
  where coats mix, the colour with more cats shows most, and at an even split the lighter one covers
  the darker, which shows only at the spot's edge. The heat is a picture of the mix, not a measure
  of it.
- **Cats with no coat noted** make smaller spots in the blue of their dots, blue rather than grey
  so they never read as a grey or black coat.
- **The heat follows the zoom.** Each cat's heat shrinks and fades as the map zooms out, so a whole
  city shows its neighbourhoods as separate spots rather than one glow over all of it, and grows back
  as the map closes in on a street.
- **Coats** opens the coat grid in a sheet, under a line saying that only cats of the marked coats
  stay on the map. Choosing coats shows only cats of those coats, and "Not specified" shows the cats
  with none noted. "Not specified" is the grid's twelfth cell, a paw marked like any coat; that cell
  is the filter's alone, and the Counter's grid and the detail's picker have none. The choice applies
  to the dots, the clusters, the heat, a focused outing and a spot's list alike; a focused outing's
  route is never thinned by it, whichever kind of line it draws. The view stays where it is when the
  choice changes. "Every coat" clears it, and is disabled while nothing is chosen. A choice that
  matches no cat says so, rather than showing a map with nothing on it.

Both last as long as the tab does; leaving the tab clears them, and so does a cat's coordinates
opening the map (above).

## At the edges

- **No cat has a location yet:** the tab says so instead of showing an empty map, and so loads no
  tiles until there is something to show.
- **A cat gets its location while the map is open:** its dot appears where it is, and the view stays
  where it was panned rather than jumping to fit it. Rotating the phone or switching the theme keeps
  that view too; only leaving the tab and coming back fits the map around the cats again.
- **No connection:** the dots sit on the map's style, so without the style there is nothing to draw
  them on. An area already seen loads from the cache; a first look with no connection says the map
  could not load, rather than showing an empty canvas.
- **No compass, scale or logo:** only the attribution sits over the map. Two fingers still turn and
  tilt it as on any map, and no control sets it straight; the view comes back north-up and flat
  whenever it is fitted again, as when an outing is focused or let go, a cat's coordinates open it,
  or the tab is left and reopened.
- **TalkBack** hears how many cats the map shows; the dots themselves are not reachable yet, and a
  spot's list is read like the Encounters tab.
- **Cats on both sides of the 180th meridian**, in Fiji or Chukotka, open on a view spanning the
  world: the fitted area runs west to east the long way round. Every dot is still on screen.
- **White cats on a light street:** each dot has the same outline the coat faces carry, so a white
  cat's dot does not disappear into the map. A fur whose heat would melt into the map's own shade —
  white on the light map, black on the dark one — gets a thin rim of that outline's colour around
  its spots, and no other fur does, so the rest keep their colour clean.

## Where the code lives

- `presentation/…/map/` — `MapState` (loading, empty, or the located points and the area to open
  on), `MapFocus` (an outing's `MapLine`s, each a list of `MapPosition`), `MapStateMapper`,
  `MapStore`; `MapSpotState`, `MapSpotStateMapper`, `MapSpotStore` — a spot's list
- `domain/…/usecase/ObserveOutingTracks.kt` — the tracks of the walks overlapping a focused outing;
  `domain/…/session/SessionSplitter.kt`'s `outingOf` — the outing that holds a cat, for the mapper and
  the tracks alike
- `ui/…/map/MapScreen.kt` — the map; `MapStyle.kt` — its light and dark style, which a cat's detail
  map shares; `CatLayers.kt` — the dots, the clusters and
  their taps; `CatHeat.kt` — the heat; `CoatDotPainter.kt` — a dot painted in its coat's colours;
  `MapFeatures.kt` — cats as map features, and each coat's colour shares; its `routeLines` — a
  focused outing's `MapLine`s as map features; `HeatInk.kt` — the heat's layers, their order and
  which need a rim; `MapSpotScreen.kt` — a spot's list, drawn by the Encounters tab's own
  `EncounterRows` in its list layout; `MapOverlay.kt` — the chips over the map; `MapAttribution.kt` —
  the attribution, the one library control kept on it; `MapCoatSheet.kt` — the coat choice
- `app/…/navigation/CatsMap.kt`, `Destinations.kt` — the tab; `MapSpot.kt` — a spot's list on the
  back stack, drawn as a sheet by `BottomSheetSceneStrategy.kt`; `MapFocusRequest.kt` — the outing,
  or the single cat, another tab or a spot's list asked the map to show

## Not built yet

A walk with no located cat of its own cannot be shown: the map only reaches a walk's track through an
outing's "On the map", and an outing with no located cat offers none. There is no list of walks to
pick one from directly.
