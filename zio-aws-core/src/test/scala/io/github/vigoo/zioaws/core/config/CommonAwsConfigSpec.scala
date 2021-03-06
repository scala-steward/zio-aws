package io.github.vigoo.zioaws.core.config

import java.net.URI

import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProvider,
  StaticCredentialsProvider
}
import software.amazon.awssdk.core.retry.conditions.{
  OrRetryCondition,
  RetryCondition
}
import software.amazon.awssdk.core.retry.{RetryMode, RetryPolicy}
import software.amazon.awssdk.regions.Region
import zio.test._
import zio.config._
import zio.config.typesafe.TypesafeConfigSource
import zio.test.environment.TestEnvironment
import zio.test.Assertion._
import zio.test.AssertionM.Render.{className, param}

import scala.reflect.ClassTag

object CommonAwsConfigSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] =
    suite("commonAwsConfig")(
      test("can read example HOCON") {
        val example =
          """region = "us-east-1"
            |credentials {
            |  accessKeyId = "ID"
            |  secretAccessKey = "SECRET"
            |}
            |
            |client {
            |  extraHeaders = [
            |    {
            |      name = "X-Test"
            |      value = "1"
            |    }
            |  ]
            |
            |  retryPolicy {
            |    mode: default
            |    numRetries: 3
            |    additionalRetryConditionsAllowed: false
            |    backoffStrategy {
            |      fullJitter {
            |        baseDelay = 100 ms
            |        maxBackoffTime = 5 s
            |      }
            |    }
            |    retryCondition {
            |      or: [
            |        { maxNumberOfRetries = 10 },
            |        { retryOnStatusCode = [500, 501] }
            |      ]
            |    }
            |    retryCapacityCondition = none
            |  }
            |}
            |""".stripMargin

        val config = for {
          source <- TypesafeConfigSource.fromHoconString(example)
          config = descriptors.commonAwsConfig from source
          result <- read(config)
        } yield result
        assert(config)(
          isRight(
            hasField[CommonAwsConfig, Option[Region]](
              "region",
              _.region,
              isSome(equalTo(Region.US_EAST_1))
            ) &&
              hasField[CommonAwsConfig, AwsCredentialsProvider](
                "credentialsProvider",
                _.credentialsProvider,
                isSubtype[StaticCredentialsProvider](
                  hasField[StaticCredentialsProvider, String](
                    "accessKeyId",
                    _.resolveCredentials().accessKeyId(),
                    equalTo("ID")
                  ) &&
                    hasField[StaticCredentialsProvider, String](
                      "secretAccessKey",
                      _.resolveCredentials().secretAccessKey(),
                      equalTo("SECRET")
                    )
                )
              ) &&
              hasField[CommonAwsConfig, Option[URI]](
                "endpointOverride",
                _.endpointOverride,
                isNone
              ) &&
              hasField[CommonAwsConfig, Option[CommonClientConfig]](
                "commonClientConfig",
                _.commonClientConfig,
                isSome(
                  hasField[CommonClientConfig, Map[String, List[String]]](
                    "extraHeaders",
                    _.extraHeaders,
                    equalTo(Map("X-Test" -> List("1")))
                  ) &&
                    hasField[CommonClientConfig, Option[RetryPolicy]](
                      "retryPolicy",
                      _.retryPolicy,
                      isSome(
                        hasField[RetryPolicy, RetryMode](
                          "mode",
                          _.retryMode(),
                          equalTo(RetryMode.defaultRetryMode())
                        ) &&
                          hasField[RetryPolicy, Int](
                            "numRetries",
                            _.numRetries(),
                            equalTo(3)
                          ) &&
                          hasField[RetryPolicy, RetryCondition](
                            "retryCondition",
                            _.retryCondition(),
                            isSubtype[OrRetryCondition](anything)
                          )
                      )
                    )
                )
              )
          )
        )
      }
    )
}
