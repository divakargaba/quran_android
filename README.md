# Poem audio

Compact copies (Opus in Ogg, mono, about 22 kbit/s) of the recitations of the poems that the app
plays, for keeping them on the device: `<recording>/<track>.ogg`, the tracks in the order of the
recording's in the app's catalog (`feature/library/src/main/assets/library/catalog.json`, which
names the reciters and where the recordings are published). They are made by
`feature/library/tools/encode_offline.py`; the times of the verses in them are those of the
recordings they are copies of.

The app downloads these files from this branch: it has to stay.
