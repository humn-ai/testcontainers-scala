package com.dimafeng.testcontainers

import java.util.Optional

import com.dimafeng.testcontainers.ContainerSpec._
import com.dimafeng.testcontainers.lifecycle.TestLifecycleAware
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.{Args, FlatSpec, Reporter}
import org.testcontainers.containers.{GenericContainer => OTCGenericContainer}
import org.testcontainers.lifecycle.{TestDescription, TestLifecycleAware => JavaTestLifecycleAware}

class ContainerSpec extends BaseSpec[ForEachTestContainer] {

  it should "call all appropriate methods of the container" in {
    val container = mock[SampleOTCContainer]

    new TestSpec({
      assert(1 == 1)
    }, new SampleContainer(container)).run(None, Args(mock[Reporter]))

    verify(container).beforeTest(any())
    verify(container).start()
    verify(container).afterTest(any(), ArgumentMatchers.eq(Optional.empty()))
    verify(container).stop()
  }

  it should "call all appropriate methods of the container if assertion fails" in {
    val container = mock[SampleOTCContainer]

    var err: Throwable = null

    new TestSpec({
      assert(1 == 2)
    }, new SampleContainer(container)).run(None, Args(mock[Reporter]))

    val captor = ArgumentCaptor.forClass[Optional[Throwable], Optional[Throwable]](classOf[Optional[Throwable]])
    verify(container).beforeTest(any())
    verify(container).start()
    verify(container).afterTest(any(), captor.capture())
    assert(captor.getValue.isPresent)
    verify(container).stop()
  }

  it should "start and stop container only once" in {
    val container = mock[SampleOTCContainer]

    new MultipleTestsSpec({
      assert(1 == 1)
    }, new SampleContainer(container)).run(None, Args(mock[Reporter]))

    verify(container, times(2)).beforeTest(any())
    verify(container).start()
    verify(container, times(2)).afterTest(any(), any())
    verify(container).stop()
  }

  it should "call afterStart() and beforeStop()" in {
    val container = mock[SampleOTCContainer]

    // ForEach
    val specForEach = Mockito.spy(new TestSpec({}, new SampleContainer(container)))
    specForEach.run(None, Args(mock[Reporter]))

    verify(specForEach).afterStart()
    verify(specForEach).beforeStop()

    // ForAll

    val specForAll = Mockito.spy(new MultipleTestsSpec({}, new SampleContainer(container)))
    specForAll.run(None, Args(mock[Reporter]))

    verify(specForAll).afterStart()
    verify(specForAll).beforeStop()
  }

  it should "call beforeStop() and stop container if error thrown in afterStart()" in {
    val container = mock[SampleOTCContainer]

    // ForEach
    val specForEach = Mockito.spy(new TestSpecWithFailedAfterStart({}, new SampleContainer(container)))
    intercept[RuntimeException] {
      specForEach.run(None, Args(mock[Reporter]))
    }
    verify(container, times(0)).beforeTest(any())
    verify(container).start()
    verify(specForEach).afterStart()
    verify(container, times(0)).afterTest(any(), any())
    verify(specForEach).beforeStop()
    verify(container).stop()

    // ForAll
    val specForAll = Mockito.spy(new MultipleTestsSpecWithFailedAfterStart({}, new SampleContainer(container)))
    intercept[RuntimeException] {
      specForAll.run(None, Args(mock[Reporter]))
    }
    verify(container, times(0)).beforeTest(any())
    verify(container, times(2)).start()
    verify(specForAll).afterStart()
    verify(container, times(0)).afterTest(any(), any())
    verify(specForAll).beforeStop()
    verify(container, times(2)).stop()
  }

  it should "not start container if all tests are ignored" in {
    val container = mock[SampleOTCContainer]
    val specForAll = Mockito.spy(new TestSpecWithAllIgnored({}, new SampleContainer(container)))
    specForAll.run(None, Args(mock[Reporter]))

    verify(container, Mockito.never()).start()
  }

  it should "work with `configure` method" in {
    val innerContainer = new SampleOTCContainer
    val container = new SampleContainer(innerContainer)
      .configure{c => c.withWorkingDirectory("123"); ()}

    assert(container.workingDirectory == "123")
  }
}

object ContainerSpec {

  protected class TestSpec(testBody: => Unit, _container: Container) extends FlatSpec with ForEachTestContainer {
    override val container = _container

    it should "test" in {
      testBody
    }
  }

  protected class TestSpecWithFailedAfterStart(testBody: => Unit, _container: Container) extends FlatSpec with ForEachTestContainer {
    override val container = _container

    override def afterStart(): Unit = throw new RuntimeException("something wrong in afterStart()")

    it should "test" in {
      testBody
    }
  }

  protected class MultipleTestsSpec(testBody: => Unit, _container: Container) extends FlatSpec with ForAllTestContainer {
    override val container = _container

    it should "test1" in {
      testBody
    }

    it should "test2" in {
      testBody
    }
  }

  protected class MultipleTestsSpecWithFailedAfterStart(testBody: => Unit, _container: Container) extends FlatSpec with ForAllTestContainer {
    override val container = _container

    override def afterStart(): Unit = throw new RuntimeException("something wrong in afterStart()")

    it should "test1" in {
      testBody
    }

    it should "test2" in {
      testBody
    }
  }

  protected class TestSpecWithAllIgnored(testBody: => Unit, _container: Container) extends FlatSpec with ForAllTestContainer {
    override val container = _container

    it should "test" ignore {
      testBody
    }
  }

  class SampleOTCContainer extends OTCGenericContainer with JavaTestLifecycleAware {

    override def beforeTest(description: TestDescription): Unit = {
      println("beforeTest")
    }

    override def afterTest(description: TestDescription, throwable: Optional[Throwable]): Unit = {
      println("afterTest")
    }

    override def start(): Unit = {
      println("start")
    }

    override def stop(): Unit = {
      println("stop")
    }
  }

  class SampleContainer(sampleOTCContainer: SampleOTCContainer)
    extends SingleContainer[SampleOTCContainer] with TestLifecycleAware {
    override implicit val container: SampleOTCContainer = sampleOTCContainer

    override def beforeTest(description: TestDescription): Unit = {
      container.beforeTest(description)
    }

    override def afterTest(description: TestDescription, throwable: Option[Throwable]): Unit = {
      container.afterTest(description, throwable.fold[Optional[Throwable]](Optional.empty())(Optional.of))
    }
  }
}
