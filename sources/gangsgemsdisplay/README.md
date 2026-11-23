# Gang Gems display companion

This experimental Fabric companion shows the selected furniture's price with
the Gang Gems icon and displays the player's current balance. The server sends
each connected player only their own balance, at most once per second. Install
the companion on both the server and client to use it. Clients without the
companion can still connect and use `/ganggems balance`.

The display is active only when the server has Gang Gems installed and Gem
furniture crafting is enabled. It reports an unavailable wallet rather than a
zero balance. Otherwise the original furniture display remains unchanged.
Disconnecting clears the client balance.

Build from the repository root after building Gang Gems 0.1.1:

```powershell
.\sources\goldclaim\gradlew.bat -p .\sources\gangsgemsdisplay build --no-daemon
```

An isolated loopback-only server with the production Gang Gems jar and display
server companion was tested with a connected development client. The owner
confirmed the icon and price, a balance update from 0 to 100, and a furniture
purchase that updated the displayed balance. The remapped production mixin
targets were checked against the pinned furniture jar. The packaged client
has not yet been tested and the companion has not been installed on the host.
