package bluewhale

import pdi.jwt.JwtAlgorithm.HS256
import pdi.jwt.{JwtClaim, JwtHeader, JwtJson}

object JwtTestUtil {

  private val jwtHeader = createHeader

  def getJwtHeader: (String, String) = jwtHeader
  
  private def createHeader: (String, String) = {
    val claim = JwtClaim().by("test").expiresIn(3600)
    val header = JwtHeader(Some(HS256))
    val token = JwtJson.encode(header, claim, "test-secret")
    ("Authorization", s"Bearer $token")
  }
}
