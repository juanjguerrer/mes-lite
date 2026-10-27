using System.Globalization;
using MesLite.Collector.Configuration;
using MesLite.Collector.Events;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Options;

namespace MesLite.Collector.Storage;

// Messages are stored before they are published, and marked as sent only once the broker confirms them.
public sealed class Outbox
{
    private readonly string _connectionString;

    public Outbox(IOptions<CollectorOptions> options)
    {
        _connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = options.Value.OutboxPath,
            Mode = SqliteOpenMode.ReadWriteCreate,
        }.ToString();
        Initialize();
    }

    private void Initialize()
    {
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = """
            PRAGMA journal_mode = WAL;
            CREATE TABLE IF NOT EXISTS outbox (
                event_id   TEXT PRIMARY KEY,
                topic      TEXT NOT NULL,
                payload    TEXT NOT NULL,
                created_at TEXT NOT NULL,
                sent_at    TEXT NULL
            );
            CREATE INDEX IF NOT EXISTS ix_outbox_pending ON outbox (created_at) WHERE sent_at IS NULL;
            """;
        command.ExecuteNonQuery();
    }

    public async Task AddAsync(OutboundMessage message, CancellationToken ct)
    {
        await using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(ct);
        await using var command = connection.CreateCommand();
        // OR IGNORE: saving the same event twice must not fail or create a second row.
        command.CommandText = """
            INSERT OR IGNORE INTO outbox (event_id, topic, payload, created_at)
            VALUES ($eventId, $topic, $payload, $createdAt);
            """;
        command.Parameters.AddWithValue("$eventId", message.eventId.ToString());
        command.Parameters.AddWithValue("$topic", message.topic);
        command.Parameters.AddWithValue("$payload", message.payload);
        command.Parameters.AddWithValue("$createdAt", DateTimeOffset.UtcNow.ToString("O", CultureInfo.InvariantCulture));
        await command.ExecuteNonQueryAsync(ct);
    }

    public async Task<IReadOnlyList<OutboundMessage>> GetPendingAsync(int limit, CancellationToken ct)
    {
        await using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(ct);
        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT event_id, topic, payload
            FROM outbox
            WHERE sent_at IS NULL
            ORDER BY created_at, rowid
            LIMIT $limit;
            """;
        command.Parameters.AddWithValue("$limit", limit);

        var pending = new List<OutboundMessage>();
        await using var reader = await command.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
        {
            pending.Add(new OutboundMessage(
                topic: reader.GetString(1),
                payload: reader.GetString(2),
                eventId: Guid.Parse(reader.GetString(0))));
        }
        return pending;
    }

    public async Task MarkSentAsync(Guid eventId, CancellationToken ct)
    {
        await using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(ct);
        await using var command = connection.CreateCommand();
        command.CommandText = "UPDATE outbox SET sent_at = $sentAt WHERE event_id = $eventId;";
        command.Parameters.AddWithValue("$sentAt", DateTimeOffset.UtcNow.ToString("O", CultureInfo.InvariantCulture));
        command.Parameters.AddWithValue("$eventId", eventId.ToString());
        await command.ExecuteNonQueryAsync(ct);
    }
}
