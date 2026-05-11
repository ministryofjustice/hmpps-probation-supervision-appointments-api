#!/usr/bin/env bash
set -euo pipefail

: "${APP_ID:?APP_ID environment variable must be set}"

QUERY_FILE="custom-events.kql"

SLACK_CHANNEL="manage-people-on-probation-alerts"
TITLE="Probation Supervision Appointments API Events"
SUMMARY="Custom event totals for yesterday."

QUERY=$(cat "$QUERY_FILE")

token=$(az account get-access-token \
  --resource https://api.applicationinsights.io \
  --query accessToken \
  -o tsv)

jq -nc --arg query "$QUERY" '{query: $query}' > request.json

curl -fsSL \
  -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  --data @request.json \
  "https://api.applicationinsights.io/v1/apps/$APP_ID/query" \
  > result.json

jq '
  if .error then
    { error: .error }
  else
    {
      columns: (.tables[0].columns | map(.name)),
      rowCount: (.tables[0].rows | length)
    }
  end
' result.json

count=$(jq -r '.tables[0].rows | length // 0' result.json)

echo "Found $count results."

if [[ "$count" -eq 0 ]]; then
  exit 0
fi

echo
echo "Event | Count"
echo "------------------------------"

jq -r '
  .tables[0].rows[]
  | "\(.[0]) | \(.[1])"
' result.json

echo

# payload=$(jq -c \
#   --arg slack_channel "$SLACK_CHANNEL" \
#   --arg title "$TITLE" \
#   --arg summary "$SUMMARY" \
#   '
#   .tables[0] as $table |
#   ($table.columns | map(.name)) as $columns |
#   ($table.rows | map(
#     . as $row |
#     reduce range(0; $columns | length) as $i
#       ({}; . + { ($columns[$i]): ($row[$i] // "N/A") })
#   )) as $rows |
#   {
#     channel: $slack_channel,
#     unfurl_links: false,
#     unfurl_media: false,
#     text: $title,
#     blocks: [
#       {
#         type: "header",
#         text: {
#           type: "plain_text",
#           text: ":information_source: \($title)",
#           emoji: true
#         }
#       },
#       {
#         type: "section",
#         text: {
#           type: "mrkdwn",
#           text: $summary
#         }
#       },
#       {
#         type: "section",
#         text: {
#           type: "mrkdwn",
#           text: (
#             "*Event* | *Count*\n" +
#             (
#               $rows
#               | map((.Event | tostring) + " | *" + (.Count | tostring) + "*")
#               | join("\n")
#             )
#           )
#         }
#       },
#       {
#         type: "context",
#         elements: [
#           {
#             type: "mrkdwn",
#             text: ">This report was generated automatically from Application Insights."
#           }
#         ]
#       }
#     ]
#   }
#   ' result.json)
#
# echo "$payload" | jq
