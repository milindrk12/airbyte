/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.integrations.source.debezium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.integrations.source.debezium.interfaces.CdcConnectorMetadata;
import io.airbyte.integrations.source.jdbc.AbstractJdbcSource;
import io.airbyte.protocol.models.AirbyteMessage;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.debezium.engine.ChangeEvent;
import java.time.Instant;

public class DebeziumEventUtils {

  public static AirbyteMessage toAirbyteMessage(ChangeEvent<String, String> event, CdcConnectorMetadata cdcConnectorMetadata, Instant emittedAt) {
    final JsonNode debeziumRecord = Jsons.deserialize(event.value());
    final JsonNode before = debeziumRecord.get("before");
    final JsonNode after = debeziumRecord.get("after");
    final JsonNode source = debeziumRecord.get("source");

    final JsonNode data = formatDebeziumData(before, after, source, cdcConnectorMetadata);
    final String schemaName = source.get("db").asText();
    final String streamName = source.get("table").asText();

    final AirbyteRecordMessage airbyteRecordMessage = new AirbyteRecordMessage()
        .withStream(streamName)
        .withNamespace(schemaName)
        .withEmittedAt(emittedAt.toEpochMilli())
        .withData(data);

    return new AirbyteMessage()
        .withType(AirbyteMessage.Type.RECORD)
        .withRecord(airbyteRecordMessage);
  }

  // warning mutates input args.
  private static JsonNode formatDebeziumData(JsonNode before, JsonNode after, JsonNode source, CdcConnectorMetadata cdcConnectorMetadata) {
    final ObjectNode base = (ObjectNode) (after.isNull() ? before : after);

    long transactionMillis = source.get("ts_ms").asLong();

    base.put(AbstractJdbcSource.CDC_UPDATED_AT, transactionMillis);
    cdcConnectorMetadata.addMetaData(base, source);

    if (after.isNull()) {
      base.put(AbstractJdbcSource.CDC_DELETED_AT, transactionMillis);
    } else {
      base.put("_ab_cdc_deleted_at", (Long) null);
    }

    return base;
  }

}
