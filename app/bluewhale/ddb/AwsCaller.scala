package bluewhale.ddb

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler

import scala.concurrent.{Future, Promise}

object AwsCaller {

  private def doCall[Request <: AmazonWebServiceRequest, Result](retryCount: Int, request: Request, fun: (Request, AsyncHandler[Request, Result]) => Unit, promise: Promise[Result]): Future[Result] = {
    fun(request, new AsyncHandler[Request, Result] {
      override def onError(exception: Exception): Unit =
        if (retryCount > 0) doCall(retryCount - 1, request, fun, promise) else promise.failure(exception)

      override def onSuccess(request: Request, result: Result): Unit =
        promise.success(result)
    })
    promise.future
  }

  def call[Request <: AmazonWebServiceRequest, Result](retryCount: Int, request: Request, fun: (Request, AsyncHandler[Request, Result]) => Unit): Future[Result] =
    doCall(retryCount, request, fun, Promise[Result])

  def callWithDefaultRetryCount[Request <: AmazonWebServiceRequest, Result](request: Request, fun: (Request, AsyncHandler[Request, Result]) => Unit): Future[Result] =
    call(500, request, fun)

}
