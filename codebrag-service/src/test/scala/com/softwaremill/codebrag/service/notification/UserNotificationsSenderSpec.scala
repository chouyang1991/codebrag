package com.softwaremill.codebrag.service.notification

import org.scalatest.{BeforeAndAfterEach, FlatSpec}
import org.scalatest.mock.MockitoSugar
import org.scalatest.matchers.ShouldMatchers
import com.softwaremill.codebrag.common.{ClockSpec, Clock}
import com.softwaremill.codebrag.service.config.CodebragConfig
import com.typesafe.config.ConfigFactory
import com.softwaremill.codebrag.domain.builder.UserAssembler
import org.mockito.Mockito._
import com.softwaremill.codebrag.dao.user.UserDAO
import com.softwaremill.codebrag.domain.LastUserNotificationDispatch
import com.softwaremill.codebrag.common.config.ConfigWithDefault
import com.softwaremill.codebrag.dao.finders.followup.FollowupFinder
import com.softwaremill.codebrag.finders.commits.toreview.ToReviewCommitsFinder
import com.softwaremill.codebrag.usecases.notifications.{RepoBranchNotificationView, UserNotificationsView, FindUserNotifications}

class UserNotificationsSenderSpec
  extends FlatSpec with MockitoSugar with ShouldMatchers with BeforeAndAfterEach with ClockSpec {

  var notificationService: NotificationService = _
  var userDao: UserDAO = _
  var followupFinder: FollowupFinder = _
  var toReviewCommitsFinder: ToReviewCommitsFinder = _
  var findUserNotifications: FindUserNotifications = _

  var sender: UserNotificationsSender = _

  val SomeFollowups = 20
  val NoFollowups = 0
  val SomeCommits = 10
  val NoCommits = 0

  override def beforeEach() {
    followupFinder = mock[FollowupFinder]
    toReviewCommitsFinder = mock[ToReviewCommitsFinder]
    userDao = mock[UserDAO]
    notificationService = mock[NotificationService]
    findUserNotifications = mock[FindUserNotifications]
    
    sender = new TestUserNotificationsSender(findUserNotifications, followupFinder, toReviewCommitsFinder, userDao, notificationService, clock)
  }

  it should "not send notification when user has notifications disabled" in {
    // given
    val user = UserAssembler.randomUser.withEmailNotificationsDisabled().get
    val userHeartbeat = clock.nowUtc.minusHours(1)
    val heartbeats = List((user.id, userHeartbeat))
    when(userDao.findById(user.id)).thenReturn(Some(user))
    when(followupFinder.countFollowupsForUserSince(userHeartbeat, user.id)).thenReturn(1)

    // when
    sender.sendInstantNotification(heartbeats)

    // then
    verifyZeroInteractions(notificationService)
  }

  it should "not send notification when user is not active " in {
    // given
    val user = UserAssembler.randomUser.withEmailNotificationsEnabled().withActive(set = false).get
    val userHeartbeat = clock.nowUtc.minusHours(1)
    val heartbeats = List((user.id, userHeartbeat))
    when(userDao.findById(user.id)).thenReturn(Some(user))
    when(followupFinder.countFollowupsForUserSince(userHeartbeat, user.id)).thenReturn(1)

    // when
    sender.sendInstantNotification(heartbeats)

    // then
    verifyZeroInteractions(notificationService)
  }

  it should "not send notification when user has no followups or commits waiting" in {
    // given
    val user = UserAssembler.randomUser.get.copy(notifications = LastUserNotificationDispatch(None, None))
    val lastHeartbeat = clock.nowUtc.minusHours(1)
    val heartbeats = List((user.id, lastHeartbeat))
    when(userDao.findById(user.id)).thenReturn(Some(user))
    when(findUserNotifications.executeSince(lastHeartbeat, user.id)).thenReturn(UserNotificationsView(followups = 0, repos = Set.empty))

    // when
    sender.sendInstantNotification(heartbeats)

    // then
    verifyZeroInteractions(notificationService)
  }

  it should "not send daily digest when user has daily digest email disabled" in {
    // given
    val user = UserAssembler.randomUser.withDailyDigestEmailDisabled().get
    val sender = new TestUserNotificationsSender(findUserNotifications, followupFinder, toReviewCommitsFinder, userDao, notificationService, clock)

    // when
    sender.sendDailyDigest(List(user))

    // then
    verifyZeroInteractions(notificationService)
  }

  it should "not send daily digest when user is not active" in {
    // given
    val user = UserAssembler.randomUser.withDailyDigestEmailEnabled().withActive(set = false).get
    val sender = new TestUserNotificationsSender(findUserNotifications, followupFinder, toReviewCommitsFinder, userDao, notificationService, clock)

    // when
    sender.sendDailyDigest(List(user))

    // then
    verifyZeroInteractions(notificationService)
    verifyZeroInteractions(followupFinder)
  }

  it should "not send daily digest when user has no commits or followups waiting" in {
    // given
    val user = UserAssembler.randomUser.get
    when(findUserNotifications.execute(user.id)).thenReturn(UserNotificationsView(followups = 0, repos = Set.empty))

    // when
    sender.sendDailyDigest(List(user))

    // then
    verifyZeroInteractions(notificationService)
  }

  it should "send notification when user has new followups" in {
    // given
    val user = UserAssembler.randomUser.get
    val lastHeartbeat = clock.nowUtc.minusHours(1)
    val heartbeats = List((user.id, lastHeartbeat))
    val notifications = UserNotificationsView(followups = 1, repos = Set.empty)
    when(userDao.findById(user.id)).thenReturn(Some(user))
    when(findUserNotifications.executeSince(lastHeartbeat, user.id)).thenReturn(notifications)

    // when
    sender.sendInstantNotification(heartbeats)

    // then
    verify(notificationService).sendFollowupAndCommitsNotification(user, notifications)
  }

  it should "send notification when user has new commits" in {
    // given
    val user = UserAssembler.randomUser.get
    val lastHeartbeat = clock.nowUtc.minusHours(1)
    val heartbeats = List((user.id, lastHeartbeat))
    val notifications = UserNotificationsView(followups = 0, repos = Set(RepoBranchNotificationView("testRepo", "testBranch", 1)))
    when(userDao.findById(user.id)).thenReturn(Some(user))
    when(findUserNotifications.executeSince(lastHeartbeat, user.id)).thenReturn(notifications)

    // when
    sender.sendInstantNotification(heartbeats)

    // then
    verify(notificationService).sendFollowupAndCommitsNotification(user, notifications)
  }

  it should "send daily digest when user has commits or followups" in {
    // given
    val user = UserAssembler.randomUser.get
    val userNotifications = UserNotificationsView(10, Set(RepoBranchNotificationView("codebrag", "master", 20)))
    when(userDao.findById(user.id)).thenReturn(Some(user))
    when(findUserNotifications.execute(user.id)).thenReturn(userNotifications)
    when(toReviewCommitsFinder.countForUserRepoAndBranch(user.id)).thenReturn(SomeCommits)
    when(followupFinder.countFollowupsForUser(user.id)).thenReturn(SomeFollowups)

    // when
    sender.sendDailyDigest(List(user))

    // then
    verify(notificationService).sendDailySummary(user, userNotifications)
  }
  
  it should "include only watched branches containing non-zero commits to review" in {
    // given
    val user = UserAssembler.randomUser.get
    val watchedBranches = Set(
      RepoBranchNotificationView("codebrag", "master", 20),
      RepoBranchNotificationView("codebrag", "bugfix", 0)
    ) 
    val userNotifications = UserNotificationsView(10, watchedBranches)
    when(userDao.findById(user.id)).thenReturn(Some(user))
    when(findUserNotifications.execute(user.id)).thenReturn(userNotifications)
    when(toReviewCommitsFinder.countForUserRepoAndBranch(user.id)).thenReturn(SomeCommits)
    when(followupFinder.countFollowupsForUser(user.id)).thenReturn(SomeFollowups)

    // when
    sender.sendDailyDigest(List(user))

    // then
    val nonZeroNotifications = userNotifications.copy(repos = userNotifications.repos.filter(_.commits > 0))
    verify(notificationService).sendDailySummary(user, nonZeroNotifications)    
  }

  class TestUserNotificationsSender(
    val findUserNotifications: FindUserNotifications,
    val followupFinder: FollowupFinder,
    val toReviewCommitsFinder: ToReviewCommitsFinder,
    val userDAO: UserDAO,
    val notificationService: NotificationService,
    val clock: Clock) extends UserNotificationsSender {

    def config = new CodebragConfig with ConfigWithDefault {
      import collection.JavaConversions._
      val params = Map("codebrag.user-email-notifications.enabled" -> "true")
      def rootConfig = ConfigFactory.parseMap(params)
    }
  }

}
