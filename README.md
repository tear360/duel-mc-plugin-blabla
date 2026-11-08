# DuelPlugin

A PvP duel / practice plugin for **Paper Minecraft 1.21.4** with:

- 1v1 duels with selectable game modes
- Party system (invite, FFA, leadership transfer)
- Friend system
- Queue system
- Bot duels (fight a bot that looks like a real player)
- Custom kits + kit editor (with VIP trims)
- Arena management with per-mode arenas
- Block-break sandbox arenas (Vanilla / UHC / DiaSMP)
- Scoreboard, tab list, spectator mode
- Join/quit and chat messages
- Discord integration (JDA bot): duel results, bug reports, player reports
- Chat filter
- Moderation commands (ban, kick, tempban, mute, unban, unmute)
- Multilingual: **English, Français, Deutsch, Español**

---

## Installation

1. Download the latest `DuelPlugin-*.jar` from the [Releases](https://github.com/tear360/duel-mc-plugin-blabla/releases) page, or build it yourself (see below).
2. Put the `.jar` inside the `plugins/` folder of your Paper 1.21.4 server.
3. Restart the server.
4. `config.yml` and the language files under `plugins/DuelPlugin/lang/` are generated automatically.

### Building from source

```bash
# Requires Maven and JDK 21+
git clone https://github.com/tear360/duel-mc-plugin-blabla.git
cd duel-mc-plugin-blabla
mvn clean package
```

The JAR is produced in `target/DuelPlugin-<version>.jar`.

---

## Configuration

Everything is configured in `plugins/DuelPlugin/config.yml`:

```yaml
# Server name / IP used in scoreboard, tab and Discord embeds
server-info:
  name: "My Server"
  ip: "play.example.com"

# Default language for new players: EN, FR, DE, ES
messages:
  default-language: "EN"
  prefix: "&8[&6Duel &ePlugin&8] &r"

# Optional GitHub update check (owner/repo), token only for private repos.
# Automatic downloads are opt-in and saved to plugins/DuelPlugin/updates/
# for manual installation. Set github-update-sha256 to verify the JAR.
github-repo: ""
github-token: ""
github-auto-download: false
github-update-sha256: ""

# Discord bot (JDA)
discord:
  bot-token: ""
  bug-report-channel-id: ""
  duel-result-channel-id: ""
  report-channel-id: ""
```

### Languages

Each language has its own editable file in `plugins/DuelPlugin/lang/`:

- `en_us.yml`
- `fr_fr.yml`
- `de_de.yml`
- `es_es.yml`

Every message in the plugin is a key in these files, so you can translate or reword anything. Color codes (`&a`, `&c`, ...) and placeholders (`%player%`, `%mode%`, ...) are supported.

---

## Commands

### Players

| Command | Description |
|---------|-------------|
| `/duel <player>` | Open the mode selector and send a duel request |
| `/acceptduel [player]` | Accept a pending duel |
| `/denyduel [player]` | Decline a pending duel |
| `/f add <player>` | Send a friend request |
| `/f accept` / `/f deny` | Accept / decline the last friend request |
| `/f remove <player>` | Remove a friend |
| `/f list` | List your friends |
| `/party create` | Create a party |
| `/party invite <player>` | Invite a player |
| `/party join` | Accept an invitation |
| `/party leave` | Leave the party |
| `/party kick <player>` | Kick a member |
| `/party transfer <player>` | Transfer leadership |
| `/party disband` | Disband the party |
| `/spec <player>` | Spectate a player in duel |
| `/report <player> <reason>` | Report a player |
| `/bugreport <bug>` | Report a bug (to Discord if configured) |
| `/settings` | Player settings (friends, duels, language) |

### Admin (permission `duelplugin.admin`)

| Command | Description |
|---------|-------------|
| `/da setlobby` | Set the lobby spawn here |
| `/da lobby build` | Toggle lobby build mode |
| `/da arena create <name> <mode>` | Create an arena |
| `/da arena delete <name>` | Delete an arena |
| `/da arena setspawn <name> <1\|2>` | Set spawn 1 or 2 |
| `/da arena setmin <name>` | Set minimum corner (block modes) |
| `/da arena setmax <name>` | Set maximum corner (block modes) |
| `/da arena info <name>` | Show arena info |
| `/da arena list` | List arenas |
| `/da arena tp <name>` | Teleport to an arena |
| `/da reload` | Reload configuration |
| `/vip set <player>` | Give VIP |
| `/vip remove <player>` | Remove VIP |
| `/ban <player> [duration] [reason]` | Ban a player |
| `/kick <player> [reason]` | Kick a player |
| `/tempban <player> <duration> [reason]` | Temporarily ban |
| `/mute <player> <duration> [reason]` | Mute a player |
| `/unban <player>` | Unban a player |
| `/unmute <player>` | Unmute a player |

---

## Game Modes

| Mode | Armor | Main weapon | Breakable blocks |
|------|-------|-------------|------------------|
| **Sword** | Diamond Prot 3 | Diamond Sword Sharp 5 + Sweeping 3 | No |
| **Axe** | Diamond (base) | Diamond Axe + Sword Sharp 5 | No |
| **UHC** | Diamond Prot 2-3 | Diamond Sword Sharp 3 + Axe Eff 3 | **Yes** |
| **Pot** | Diamond Prot 4 + Unb 3 | Diamond Sword Sharp 5 + Unb 3 | No |
| **NethPot** | Netherite Prot 4 + Unb 3 + Mending | Netherite Sword Sharp 5 + Unb 3 | No |
| **Mace** | Netherite Prot 4 | Mace Breach 4 + Density 5 / Wind Burst 1 | No |
| **Vanilla** | Netherite Prot 4 + Unb 3 + Mending | Netherite Sword Sharp 5 | **Yes** |
| **SMP** | Netherite Prot 4 + Mending | 2x Netherite Swords (KB I + normal) | No |
| **DiaSMP** | Diamond Prot 4 + Mending | Diamond Sword + Axe Sharp 5 | **Yes** |
| **Spear-Mace** | Netherite Prot 4 | Mace Density 5 + Trident Impaling 5 | No |

All kits are fully customizable through the kit editor and/or directly in the language kit files.

---

## Setting up an arena

1. Create the arena:
   ```
   /da arena create myArena Sword
   ```
2. Stand at player 1's spawn and run:
   ```
   /da arena setspawn myArena 1
   ```
3. Stand at player 2's spawn and run:
   ```
   /da arena setspawn myArena 2
   ```
4. (Optional, required for block modes) Define the protected area:
   ```
   /da arena setmin myArena    # stand at the minimum corner
   /da arena setmax myArena    # stand at the maximum corner
   ```
5. Verify:
   ```
   /da arena info myArena
   ```

### Block modes (Vanilla / UHC / DiaSMP)

In these modes players can break and place blocks during the duel. A snapshot is taken at the start and everything is restored when the duel ends. The outer walls of the arena are unbreakable.

---

## Discord integration

The plugin can run a JDA bot to send:

- Duel results to a channel
- `/bugreport` submissions as forum posts
- `/report` submissions as forum posts

Configure it in `config.yml` under `discord:`. Leave `bot-token` empty to disable. The bot needs these permissions on your server: `Send Messages`, `Create Public Threads`, `Create Private Threads`, `Manage Threads`, `Read Message History`, `Add Reactions`.

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `duelplugin.admin` | Admin commands (/da) and sub-permissions | OP |
| `duelplugin.play` | Can play duels | true (everyone) |
| `duelplugin.vip` | VIP features | OP |
| `duelplugin.acceptduel` | Can accept duel requests | OP |
| `duelplugin.bugreport.bypass` | Bypass /bugreport cooldown | OP |

---

## Tech

- **Minecraft version:** Paper 1.21.4 (It might still work on higher versions, test at your own risk!)
- **API:** Paper API
- **Java:** 21+
- **Build:** Maven (maven-shade-plugin bundles dependencies)

## License

MIT
