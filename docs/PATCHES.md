# Patches

Choose a game in launcher > Details > Apply patch, then select an IPS/BPS
file through SAF. A new suffixed ROM is created in the local library, and
the original is left unchanged. IPS supports copy/RLE/resizing and bounded
output. BPS verifies source, patch, and output CRC32 before succeeding.

xdelta3/VCDIFF is not implemented. It requires review of upstream source
and license, JNI native decoder and compatibility tests on both ARM ABIs.
Until then, unsupported patches are rejected rather than corrupting ROMs.
