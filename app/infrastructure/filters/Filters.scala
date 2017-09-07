package infrastructure.filters

import javax.inject.Inject

import bluewhale.JwtFilter
import play.api.http.HttpFilters
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter

class Filters @Inject()(gzip: GzipFilter, securityHeaders: SecurityHeadersFilter, jwtFilter: JwtFilter) extends HttpFilters {
  val filters = Seq(jwtFilter, gzip, securityHeaders)
}
