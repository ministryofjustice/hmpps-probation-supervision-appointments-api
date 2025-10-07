package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.stereotype.Service

@Service
class UserService(val graphServiceClient: GraphServiceClient) {

  fun getUsers(query: String?): List<String?> {
    val searchClause = query
      ?.takeIf { it.isNotBlank() }
      ?.let { q ->
        // Microsoft Graph requires the search string in quotes
        "\"displayName:$q\" OR \"mail:$q\" OR \"userPrincipalName:$q\""
      }

    val users = graphServiceClient
      .users()[
      { requestConfiguration ->
        requestConfiguration.queryParameters.select = arrayOf(
          "id",
          "displayName",
          "mail",
          "userPrincipalName",
          "jobTitle",
        )
        requestConfiguration.queryParameters.filter =
          "accountEnabled eq true and userType eq 'Member'"

        if (searchClause != null) {
          requestConfiguration.queryParameters.search = searchClause
        }

        // Required when using $search (harmless if always included)
        requestConfiguration.headers.add("ConsistencyLevel", "eventual")
      },
    ].value

    val usersWithMail = users.filter { it.mail != null }

    return usersWithMail.map { "${it.userPrincipalName}, ${it.jobTitle}" }
  }

  fun countNumberOfUser(): Int = graphServiceClient.users().count()[
    { requestConfiguration ->
      requestConfiguration.headers.add("ConsistencyLevel", "eventual")
      requestConfiguration.queryParameters.filter =
        "accountEnabled eq true and userType eq 'Member'"
    },
  ]
}
