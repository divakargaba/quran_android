Mutashabihat ul Quran data (`phrases.json`, `phrase_verses.json`), as provided.

`phrases.json` maps a phrase id to the ayah it was taken from (`source`, word positions `from`..`to`)
and every ayah it occurs in (`ayah`: verse key to word ranges). `phrase_verses.json` maps a verse
key to the ids of the phrases it contains. Word positions count words only (no ayah markers).

The app uses `src/main/assets/mutashabihat.mtj`, a gzipped JSON built from these two files plus
the Uthmani text of each ayah involved (one entry per word, from api.quran.com) so the phrases can
be shown and highlighted.
