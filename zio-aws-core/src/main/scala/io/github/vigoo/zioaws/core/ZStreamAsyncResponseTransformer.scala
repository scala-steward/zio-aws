package io.github.vigoo.zioaws.core

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import software.amazon.awssdk.core.async.{AsyncResponseTransformer, SdkPublisher}
import zio._
import zio.stream._
import zio.interop.reactivestreams._

class ZStreamAsyncResponseTransformer[Response](resultStreamPromise: Promise[Throwable, Stream[AwsError, Byte]],
                                                responsePromise: Promise[Throwable, Response],
                                                errorPromise: Promise[Throwable, Unit])
                                               (implicit runtime: Runtime[Any])
  extends AsyncResponseTransformer[Response, Task[StreamingOutputResult[Response, Byte]]] {

  override def prepare(): CompletableFuture[Task[StreamingOutputResult[Response, Byte]]] =
    CompletableFuture.completedFuture {
      for {
        response <- responsePromise.await
        stream <- resultStreamPromise.await
      } yield StreamingOutputResult(response, stream)
    }

  override def onResponse(response: Response): Unit = {
    runtime.unsafeRun(responsePromise.complete(ZIO.succeed(response)))
  }

  override def onStream(publisher: SdkPublisher[ByteBuffer]): Unit = {
    runtime.unsafeRun(
      resultStreamPromise.complete(errorPromise.poll.flatMap { opt =>
        opt.getOrElse(ZIO.unit) *> ZIO.effect(
          publisher
            .toStream()
            .interruptWhen(errorPromise)
            .map(Chunk.fromByteBuffer)
            .flattenChunks
            .concat(ZStream.fromEffectOption[Any, Throwable, Byte](
              errorPromise.poll.flatMap {
                case None => ZIO.fail(None)
                case Some(p) => p.mapError(Some.apply) *> ZIO.fail(None)
              }))
            .mapError(AwsError.fromThrowable)
        )
      }))
  }

  override def exceptionOccurred(error: Throwable): Unit =
    runtime.unsafeRun(errorPromise.fail(error))
}

object ZStreamAsyncResponseTransformer {
  def apply[Response](): UIO[ZStreamAsyncResponseTransformer[Response]] =
    ZIO.runtime.flatMap { implicit runtime: Runtime[Any] =>
      for {
        resultStreamPromise <- Promise.make[Throwable, Stream[AwsError, Byte]]
        responsePromise <- Promise.make[Throwable, Response]
        errorPromise <- Promise.make[Throwable, Unit]
      } yield new ZStreamAsyncResponseTransformer[Response](resultStreamPromise, responsePromise, errorPromise)
    }
}
