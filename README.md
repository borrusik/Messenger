# Messenger

Java client-server messenger for the university ICQ assignment.

## Features

- custom server based on `ServerSocket`;
- multiple clients through `Socket`;
- one thread per client;
- JavaFX GUI for client and server;
- custom XML protocol;
- SQLite database through JDBC;
- common `Server Chat`;
- private messages;
- file messages in private chats;
- voice messages recorded as WAV in private chats;
- unread counters for chats;
- message receipts: one check sent, two checks delivered, blue two checks read;
- contact statuses: online/offline;
- chats stay visible when a user leaves;
- nickname change without reconnecting;
- separate conversation viewer in the server GUI.

## Run

Server:

```text
server.ServerGuiLauncher
```

Opening the server window starts the server automatically.

Client:

```text
client.ClientGuiLauncher
```

Client login screen has only:

```text
Server IP: 127.0.0.1
User name: any unique name
```

The client uses port `8080` by default. The dev script can pass another port.
The server stores data in `data/messenger.db`.

## Client Usage

- `Server Chat` is the common chat for all clients.
- Contacts are shown on the right.
- Blue dot means common server chat.
- Green dot means online.
- Red dot means offline.
- Offline chats can be opened and read, but sending is disabled.
- When a user returns online, the old chat becomes writable again.
- Press `+` near the message field to send a file in a private chat.
- Press `Rec` to start recording a voice message, then `Stop` to send it.
- Attachments have `Save`; voice messages also have `Play`.
- Chats on the right show unread message counters.
- Private outgoing messages show one check after sending, two checks after delivery, and blue two checks after reading.
- Your nickname is shown above the chat list.
- Press the pencil button next to your nickname to open the rename dialog.
- When a nickname is changed, all clients receive a system message like `old changed nickname to new`.

## XML Protocol

Each message is sent as one XML line:

```xml
<message>
    <type>TEXT</type>
    <from>alice</from>
    <to>bob</to>
    <text>Hello</text>
</message>
```

Main message types:

| Type | Purpose |
|---|---|
| `LOGIN` | user login |
| `RENAME` | nickname change |
| `TEXT` | private message |
| `FILE` | private file attachment |
| `AUDIO` | private voice message |
| `BROADCAST` | common chat message |
| `RECEIPT` | delivered/read receipt |
| `SYSTEM` | server system message |
| `USER_LIST` | online user list |
| `DISCONNECT` | client disconnect |
| `ERROR` | error message |

## Database

SQLite is used through JDBC. The database file is created automatically:

```text
data/messenger.db
```

Tables:

| Table | Stored data |
|---|---|
| `users` | known usernames and last seen time |
| `nickname_events` | old nickname, new nickname, timestamp |
| `messages` | conversation, message type, sender, recipient, text, timestamp |

The database file is ignored by Git because it is local runtime data.

To clear the database in one command, stop the server and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\clear-db.ps1
```

## Architecture

- `MessengerServer` accepts connections, stores online clients, and routes messages.
- `DatabaseService` creates SQLite tables and saves users, messages, and nickname changes.
- `ClientHandler` serves one socket client in a separate thread.
- `ConversationRegistry` stores conversations for the server GUI.
- `ClientConnection` handles networking on the client side.
- `ClientGuiApp` and `ServerGuiApp` handle JavaFX UI.

## Demo Checklist

1. Start the server.
2. Start several clients.
3. Send a message to `Server Chat`.
4. Send a private message.
5. Show online/offline statuses.
6. Disconnect a client and show that the chat remains visible.
7. Reconnect the client and show that the old chat is writable again.
8. Change nickname using the pencil button.
9. Select separate conversations in the server GUI.
10. Open `XmlProtocol.java` and explain the XML format.
