package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.stereotype.Service

@Service
class CalendarService(val graphServiceClient: GraphServiceClient)
