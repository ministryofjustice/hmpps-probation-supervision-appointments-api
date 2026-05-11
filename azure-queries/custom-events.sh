#!/usr/bin/env bash
set -euo pipefail

: "${APP_ID:?APP_ID environment variable must be set}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

QUERY_FILE="$SCRIPT_DIR/custom-events.kql"
REQUEST_FILE="$SCRIPT_DIR/request.json"
RESULT_FILE="$SCRIPT_DIR/result.json"

SLACK_CHANNEL="manage-people-on-probation-alerts"
TITLE="Probation Supervision Appointments API Events"
SUMMARY="Custom event totals for $(TZ=Europe/London date -v-1d +'%A %d-%m-%Y')."

QUERY=$(cat "$QUERY_FILE")

token=$(az account get-access-token \
  --resource https://api.applicationinsights.io \
  --query accessToken \
  -o tsv)

jq -nc --arg query "$QUERY" '{query: $query}' > "$REQUEST_FILE"

curl -fsSL \
  -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  --data @"$REQUEST_FILE" \
  "https://api.applicationinsights.io/v1/apps/$APP_ID/query" \
  > "$RESULT_FILE"

if jq -e '.error' "$RESULT_FILE" > /dev/null; then
  echo "Application Insights query failed:"
  jq '.error' "$RESULT_FILE"
  exit 1
fi

jq '{
  columns: (.tables[0].columns | map(.name)),
  rowCount: (.tables[0].rows | length)
}' "$RESULT_FILE"

count=$(jq -r '.tables[0].rows | length // 0' "$RESULT_FILE")

echo "Found $count results."

echo
echo "Event | Count"
echo "------------------------------"

if [[ "$count" -eq 0 ]]; then
  echo "No events found."
else
  jq -r '
    .tables[0].rows[]
    | "\(.[0]) | \(.[1])"
  ' "$RESULT_FILE"
fi

echo

payload=$(jq -c \
  --arg slack_channel "$SLACK_CHANNEL" \
  --arg title "$TITLE" \
  --arg summary "$SUMMARY" \
  '
  .tables[0] as $table |
  ($table.columns | map(.name)) as $columns |
  ($table.rows | map(
    . as $row |
    reduce range(0; $columns | length) as $i
      ({}; . + { ($columns[$i]): ($row[$i] // "N/A") })
  )) as $rows |
  {
    channel: $slack_channel,
    unfurl_links: false,
    unfurl_media: false,
    text: $title,
    blocks: [
      {
        type: "header",
        text: {
          type: "plain_text",
          text: ":information_source: \($title)",
          emoji: true
        }
      },
      {
        type: "section",
        text: {
          type: "mrkdwn",
          text: $summary
        }
      },
      {
        type: "section",
        text: {
          type: "mrkdwn",
          text: (
            if ($rows | length) == 0 then
              "No events found."
            else
              "*Event* | *Count*\n" +
              (
                $rows
                | map((.Event | tostring) + " | *" + (.Count | tostring) + "*")
                | join("\n")
              )
            end
          )
        }
      },
      {
        type: "context",
        elements: [
          {
            type: "mrkdwn",
            text: ">This report was generated automatically from Application Insights."
          }
        ]
      }
    ]
  }
  ' "$RESULT_FILE")

echo "$payload" | jq