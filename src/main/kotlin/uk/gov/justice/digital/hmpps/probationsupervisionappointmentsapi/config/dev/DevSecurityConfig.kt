package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.dev

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@Profile("h2-mem")
class DevSecurityConfig {

  @Bean
  fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

  @Bean
  fun userDetailsService(encoder: PasswordEncoder): UserDetailsService {
    val user = User.withUsername("user")
      .password(encoder.encode("password"))
      .roles("PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS")
      .build()

    return InMemoryUserDetailsManager(user)
  }

  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .csrf { it.disable() }
      .headers { it.frameOptions { fo -> fo.sameOrigin() } }
      .authorizeHttpRequests {
        it
          .requestMatchers("/h2-console/**", "/health").permitAll()
          .requestMatchers("/calendar/**").hasAnyRole("PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS")
          .anyRequest().authenticated()
      }
      .httpBasic { } // enable HTTP Basic

    return http.build()
  }
}
