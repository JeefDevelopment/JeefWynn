# Discord bridge service

This service relays a single configured Discord channel to linked JeefWynn clients over WebSockets. It stores no database: pending link codes live for ten minutes in memory, and completed identities are signed into client-held tokens.

## Discord setup

1. Create an application and bot in the Discord Developer Portal.
2. Enable the **Message Content Intent** for the bot.
3. Invite it to your guild with permission to view the target channel, send messages, read message history, and use application commands.
4. Note the guild ID and channel ID.

The service replaces this bot's guild-scoped slash commands with its `/link` command each time it starts. Use a dedicated bot application if it has other guild commands.

## Environment

Copy `.env.example` for local development. Production values should be secrets:

- `DISCORD_TOKEN`: bot token.
- `DISCORD_GUILD_ID`: the only guild allowed to redeem links.
- `DISCORD_CHANNEL_ID`: the relayed text channel.
- `TOKEN_SECRET`: at least 32 random characters; changing it unlinks all clients.
- `PORT`: optional, defaults to `8080`.

Generate a signing secret with `openssl rand -base64 48` or an equivalent cryptographically secure generator.

## Fly.io deployment

Fly.io does **not** offer a free tier to new customers. The example uses one 256 MB shared machine with autostop and zero minimum running machines to minimize cost, but stopped root filesystems and running time may still be billed.

```bash
cp fly.toml.example fly.toml
# Edit the unique app name and preferred region.
fly launch --no-deploy
fly secrets set DISCORD_TOKEN=... DISCORD_GUILD_ID=... DISCORD_CHANNEL_ID=... TOKEN_SECRET=...
fly deploy
```

Use the resulting `https://<app>.fly.dev` URL as the companion's bridge URL. No volume is required. Autostop can make the first connection after idle take a few seconds.

## Security behavior

- Discord tokens and signing secrets remain server-side.
- Discord guild membership is checked by where `/link` is redeemed.
- Link codes expire after ten minutes and can be used once.
- Mentions from Minecraft are neutralized; Discord `allowedMentions` is disabled.
- Client payloads are limited to 500 characters and five messages per ten seconds per connection.
- Inbound messages are limited to the configured channel; bot messages are ignored to prevent loops.

The Discord identity is verified by guild-scoped command redemption. The Minecraft name/UUID is supplied by the client and is not cryptographically attested, so do not use it for permissions or moderation decisions.

For a larger or hostile community, add server-side revocation, audit logging with a retention policy, and distributed rate limiting. The stateless design is intended for a small private guild.
