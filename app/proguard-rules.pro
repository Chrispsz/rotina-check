# ============================================================================
# RotinaCheck — R8 shrinking rules (release).
#
# Compose ships consumer rules inside its artifacts. The app has no
# reflection-based stack (no Room/Hilt), so nothing extra needs keeping.
# DataStore + org.json (platform) are reflection-free in this app's usage.
# ============================================================================
