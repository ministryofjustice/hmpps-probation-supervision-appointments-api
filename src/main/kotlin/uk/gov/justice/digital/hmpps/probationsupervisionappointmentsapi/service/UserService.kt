package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.FilteredUsers

@Service
class UserService(private val graphServiceClient: GraphServiceClient) {

  fun getUsers(query: String?): FilteredUsers {
    val searchClause = query
      ?.takeIf { it.isNotBlank() }
      ?.let { q ->
        "\"displayName:$q\" OR \"mail:$q\" OR \"userPrincipalName:$q\""
      }

    val users = graphServiceClient
      .users()[
      { requestConfiguration ->
        requestConfiguration.queryParameters?.apply {
          select = arrayOf(
            "id",
            "displayName",
            "mail",
            "userPrincipalName",
            "jobTitle",
          )
          filter = "accountEnabled eq true and userType eq 'Member'"

          if (searchClause != null) {
            search = searchClause
          }
        }

        // Required when using $search
        requestConfiguration.headers.add("ConsistencyLevel", "eventual")
      },
    ]
      ?.value
      .orEmpty() // 👈 converts null → emptyList()

    val usersWithMail = users.filter { it.mail != null }

    return FilteredUsers(
      usersWithMail.map {
        listOfNotNull(it.userPrincipalName, it.jobTitle)
          .joinToString(" - ")
      },
    )
  }

  fun countNumberOfUser(): Int = graphServiceClient.users().count()[
    { requestConfiguration ->
      requestConfiguration.headers.add("ConsistencyLevel", "eventual")
      requestConfiguration.queryParameters?.filter =
        "accountEnabled eq true and userType eq 'Member'"
    },
  ] ?: 0
}
