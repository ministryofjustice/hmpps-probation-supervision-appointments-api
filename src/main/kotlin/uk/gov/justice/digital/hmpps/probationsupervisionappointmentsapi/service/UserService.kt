package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.FilteredUsers

@Service
class UserService(val graphServiceClient: GraphServiceClient) {

  fun getUsers(query: String?): FilteredUsers {
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

    return FilteredUsers(usersWithMail.map { listOfNotNull(it.userPrincipalName, it.jobTitle).joinToString(separator = " - ") })
  }

  fun countNumberOfUser(): Int = graphServiceClient.users().count()[
    { requestConfiguration ->
      requestConfiguration.headers.add("ConsistencyLevel", "eventual")
      requestConfiguration.queryParameters.filter =
        "accountEnabled eq true and userType eq 'Member'"
    },
  ]
}
