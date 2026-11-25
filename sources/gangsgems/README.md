# Gang Gems prototype

The Discord listener and direct webhook are disabled by default. On the hosted
server the owner enabled the Discord listener and Gem furniture crafting. One
real Ko-fi order arrived as pending because checkout did not collect a username;
the owner assigned it and confirmed delivery and the thank-you. The listing is
unpublished while recipient collection is fixed. The owner also confirmed
that a Gem craft left the physical material stack unchanged. Client
display has been confirmed live; automatic recipient collection remains unverified.

An isolated Minecraft smoke test loaded the checksum-verified
pinned Discord Integration Fabric 3.0.7 mod and its bundled dependencies. A
synthetic JDA webhook event credited a cached offline recipient exactly once
and survived a wallet reload. The smoke test also invoked the login notification
handler and verified the thank-you message and saved receipt. It did not test
an actual player connection, connect a bot to Discord, or process a real Ko-fi
payment. Later isolated server tests covered the furniture opt-in and rollback,
and checked staff grants and admin-only removals with LuckPerms 5.4.102.
An isolated staging server also connected a separate test bot to Discord and
processed synthetic messages sent through a test webhook. It credited 100 Gems
once for a duplicate order, rejected an invalid token and underpayment,
recorded an unknown recipient as pending, and persisted an offline
notification. A separate local test using all 275 server-compatible indexed
pack entries repeated those checks and verified the wallet, queued notification,
and duplicate protection after a server restart. With the furniture gate
enabled, the full-pack smoke mod also checked Gem crafting, delivery recovery,
and LuckPerms staff permissions. These tests did not verify the production
relay's end-to-end delivery, a real purchase, or an actual player login.

When explicitly enabled, the prototype uses a virtual balance to craft
Immersive Furniture and disables the original crafting-material,
furniture-duplication, and recycling recipes. This gameplay change is off by
default; enabling it requires `config\gangsgems-furniture.json` containing
`{"enabled":true}` before a restart. Do not enable it until payment delivery,
full-pack behavior and client presentation have been verified.
The Discord listener accepts the recipient's Minecraft username as the entire
Ko-fi order message, so purchases can be gifted. If the message is blank, it
uses the name Ko-fi sends as `from_name` instead; buyers should sign in and set
their display name to their exact Minecraft login username before paying.
The listener cannot verify that a buyer signed in, and guest checkout may be
available. A nonblank message takes precedence, so it must contain only the
gift recipient's Minecraft username; an invalid message never falls back to
the account name. It credits the wallet directly rather than running a
server command. Orders with a
missing, invalid, expired, ambiguous, or unresolved username are saved for an
operator to assign. Offline players are resolved from the server's unexpired
local player cache; the listener does not look up unknown names online.
The separate direct webhook still uses a one-use `/ganggems link` code. Rank
discounts are not planned. Do not publish an offer until the message flow and
delivery have been verified.

The draft listing explains that some furniture has functional uses, buyers
must provide the recipient's exact Minecraft username, and some orders may
require staff assistance. Review the Minecraft commercial usage guidelines
before publication.

## Staff commands

Players can use `/gb` as a shortcut for `/ganggems balance`.
Staff can also use `/gb <player>` with the same permissions as the original.

LuckPerms groups
`mod`, `moderator`, `admin`, and `owner` may monitor, grant, credit and assign;
only `admin` and `owner` may remove Gems. Inherited groups count. The server
console may use all commands. Player staff access is denied if LuckPerms is
unavailable, even for an operator, to prevent an operator who is only a
moderator from removing Gems.

- `/ganggems grant <player> <gems> <reason>`: grant Gems for a non-purchase.
- `/ganggems remove <player> <gems> <reason>`: remove Gems without allowing a
  negative balance. Admins and owners only.
- `/ganggems balance <player>`: check a known player's balance and count of
  pending furniture deliveries.
- `/ganggems status`: show wallet, Discord connection, direct webhook and
  furniture-gate status. A connected listener does not prove payment delivery.
- `/ganggems pending [page]`: list orders awaiting player assignment.
- `/ganggems transaction <transaction>`: inspect a recorded order.
- `/ganggems history <player> [page]`: list a known player's recorded purchase
  transaction IDs and amounts, sorted by ID (not by purchase time).
- `/ganggems audit [page]`: inspect staff balance changes and purchase recovery
  actions, with actor UUID, player UUID, reason and timestamp.
- `/ganggems credit <transaction> <player> <gems>`: manually credit a verified
  purchase that was not recorded. Use the original transaction ID: replaying
  the same ID, player and amount is safe; a conflicting replay is rejected.
- `/ganggems assign <transaction> <player>`: assign a recorded pending order.

Grants, removals, manual credits and assignments are recorded atomically with
the wallet. Grants are not purchase credits; repeating a grant grants again.
Verify purchases independently: these commands do not authenticate payments.
An order absent from `/ganggems transaction` may still exist on Ko-fi, notably
if Discord or the server was offline. Player arguments must resolve to one
known player. Keep staff audit and purchase channels private.

## Discord listener configuration

The listener is enabled on the hosted server, but automatic name-based
delivery is not yet deployed or verified with a real purchase.
It reuses the Discord Integration bot and accepts only messages sent by the
configured webhook ID to the configured channel ID. It also checks the Ko-fi
verification token, shop product, currency, quantity, and configured minimum
price before recording a transaction. The bot needs access to that channel and
message content. A Discord message alone is not proof of payment.
If the bot is still starting when the server starts, the listener retries every
100 server ticks; invalid configuration remains disabled and is logged.
Do not enable the separate direct webhook at the same time: it uses a different
player-identification flow. The Discord listener refuses to start if both are
enabled.

Copy `gangsgems-discord.example.json` to `config\gangsgems-discord.json` on the
server. It contains the chosen prices and Gem amounts, and the draft listing's
link code and variant names. The mapping is provisional until the exact
`direct_link_code` and `variation_name` in a real order are verified privately.
Configure the channel ID and the sending Discord webhook ID. For example:
`{"code":"PRODUCT_CODE","variation":null,"cents":200,"gems":100}`.
The code is the Ko-fi direct-link product code, not a Discord webhook URL.
`cents` is a whole-number USD price and must be at least 200. Supply
`GANGS_KOFI_TOKEN` as a private server environment variable, or privately
create `config\gangsgems-kofi-token.txt` containing only the token. The
environment variable takes precedence, including when it is invalid. The file
must be a regular file, not a symbolic link. Restrict panel and filesystem
access to trusted administrators, and never put the token in the JSON file,
chat, logs, repository, or published pack. The direct HTTPS receiver still
requires the environment variable.

Only then set `enabled` to `true` for a controlled test. This listener does
not replay Discord history: orders received while the server or bot is offline
need manual reconciliation. Restrict access to the order channel; raw Ko-fi
messages can contain customer information and the verification token. If the
token was exposed to untrusted users, rotate it if Ko-fi permits and update the
private configuration.

For local unit tests and a build, run from the repository root:

```powershell
.\sources\goldclaim\gradlew.bat -p .\sources\gangsgems test build --no-daemon
```

The optional `runGemsSmoke` task starts an isolated Minecraft test server in
`tmp\gangsgems-smoke-20261122`. Before running it, create that directory with
an accepted Minecraft `eula.txt` and a `server.properties` containing
`server-ip=127.0.0.1`, `server-port=0`, and `online-mode=false`. Without
`config\gangsgems-furniture.json`, it checks that the original recipes and
material crafting still work. With that file containing `{"enabled":true}`,
it checks Gem crafting and delivery instead. This is a local test only, not
the hosted server.

`remapSmokeJar` builds a server-only smoke-test mod for a separate, isolated
production-mapped Fabric test server. It runs fixture actions and stops that
server, so never install it on the hosted server.

The additional Discord smoke test is enabled with the JVM property
`gangsgems.testDiscord=true`. It requires the pinned Discord Integration mod
and its bundled dependencies on the isolated Fabric runtime classpath. It
uses synthetic messages and does not connect to Discord.

For the optional staff-permission smoke test, supply the checksum-verified
LuckPerms and Fabric permissions jars using `-PsmokeLuckPermsJar=<path>` and
`-PsmokePermissionsJar=<path>` to `runGemsSmoke`.
