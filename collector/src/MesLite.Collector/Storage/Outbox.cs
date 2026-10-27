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
                event_id     TEXT PRIMARY KEY,
                topic        TEXT NOT NULL,
                payload      TEXT NOT NULL,
                created_at   TEXT NOT NULL,
                sent_at      TEXT NULL,
                line_code    TEXT NOT NULL,
                machine_code TEXT NOT NULL,
                sequence     INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS ix_outbox_pending ON outbox (created_at) WHERE sent_at IS NULL;
            CREATE UNIQUE INDEX IF NOT EXISTS ux_outbox_machine_sequence
                ON outbox (line_code, machine_code, sequence);
            """;
        command.ExecuteNonQuery();
    }

    public async Task<bool> AddAsync(OutboundMessage message, CancellationToken ct)
    {
        await using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(ct);
        await using var command = connection.CreateCommand();
        // OR IGNORE: saving the same event twice must not fail or create a second row.
        command.CommandText = """
            INSERT OR IGNORE INTO outbox (event_id, topic, payload, line_code, machine_code, sequence, created_at)
            VALUES ($eventId, $topic, $payload, $line, $machine, $sequence, $createdAt);
            """;
        command.Parameters.AddWithValue("$eventId", message.eventId.ToString());
        command.Parameters.AddWithValue("$topic", message.topic);
        command.Parameters.AddWithValue("$payload", message.payload);
        command.Parameters.AddWithValue("$createdAt", DateTimeOffset.UtcNow.ToString("O", CultureInfo.InvariantCulture));
        command.Parameters.AddWithValue("$line", message.line);
        command.Parameters.AddWithValue("$machine", message.machine);
        command.Parameters.AddWithValue("$sequence", message.sequence);
        var rowsAffected = await command.ExecuteNonQueryAsync(ct);
        return rowsAffected > 0;
    }

    public async Task<IReadOnlyList<OutboundMessage>> GetPendingAsync(int limit, CancellationToken ct)
    {
        await using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(ct);
        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT event_id, topic, payload, line_code, machine_code, sequence
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
                eventId: Guid.Parse(reader.GetString(0)),
                line: reader.GetString(3),
                machine: reader.GetString(4),
                sequence: reader.GetInt64(5)
            ));
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

    public Dictionary<(string Line, string Machine), long> GetLastSequences()
    {
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT line_code, machine_code, MAX(sequence) AS last_sequence
            FROM outbox
            GROUP BY line_code, machine_code;
            """;

        var lastSequences = new Dictionary<(string Line, string Machine), long>();
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            var line = reader.GetString(0);
            var machine = reader.GetString(1);
            var lastSequence = reader.GetInt64(2);
            lastSequences[(line, machine)] = lastSequence;
        }
        return lastSequences;
    }
}
