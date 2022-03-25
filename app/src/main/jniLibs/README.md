This aren't really JNI libraries, they're precompiled zipalign binaries.

The reason these must be here is because Android copies each
to `/data/app/<package>/lib/<arch>/<lib>.so`, which is one of the few places that files can be
marked exec.

Copying to there manually doesn't work either btw.
