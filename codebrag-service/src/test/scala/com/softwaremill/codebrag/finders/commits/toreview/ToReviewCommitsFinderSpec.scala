package com.softwaremill.codebrag.finders.commits.toreview

import org.scalatest.{BeforeAndAfter, FlatSpec}
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.mock.MockitoSugar
import com.softwaremill.codebrag.dao.user.{UserDAO, TeamDAO}
import com.softwaremill.codebrag.domain.builder.{UserAssembler, TeamAssembler}
import com.softwaremill.codebrag.cache.{RepositoriesCache, BranchCommitCacheEntry, UserReviewedCommitsCacheEntry}
import com.softwaremill.codebrag.common.paging.PagingCriteria
import org.mockito.Mockito._
import com.softwaremill.codebrag.common.ClockSpec
import com.softwaremill.codebrag.finders.browsingcontext.{UserBrowsingContext, UserBrowsingContextFinder}
import com.softwaremill.codebrag.domain.PartialUserDetails

class ToReviewCommitsFinderSpec extends FlatSpec with ShouldMatchers with MockitoSugar with BeforeAndAfter with ClockSpec {

  var finder: ToReviewCommitsFinder = _

  var repositoriesCache: RepositoriesCache = _
  var userDao: UserDAO = _
  var teamDao: TeamDAO = _
  var browsingContextFinder: UserBrowsingContextFinder = _
  var toReviewFilter: ToReviewBranchCommitsFilter = _

  val MasterBranch = "master"
  val CodebragRepo = "codebrag"

  val Bob = UserAssembler.randomUser.get
  val BobsTeam = TeamAssembler.randomTeam(Bob).get
  val BobCacheEntry = UserReviewedCommitsCacheEntry(Bob.id, CodebragRepo, Set.empty, clock.now)
  val BobUserDetails = PartialUserDetails(Bob.id, Bob.name, Bob.emailLowerCase, null, Bob.aliases)

  val NoCommitsInBranch = List.empty[BranchCommitCacheEntry]

  before {
    repositoriesCache = mock[RepositoriesCache]
    userDao = mock[UserDAO]
		teamDao = mock[TeamDAO]
    browsingContextFinder = mock[UserBrowsingContextFinder]
    toReviewFilter = mock[ToReviewBranchCommitsFilter]

    finder = new ToReviewCommitsFinder(repositoriesCache, userDao, teamDao, browsingContextFinder, toReviewFilter)

    when(userDao.findById(Bob.id)).thenReturn(Some(Bob))
    when(teamDao.findByUser(Bob.id)).thenReturn(List(BobsTeam))
    when(userDao.findPartialUserDetails(List(Bob.id))).thenReturn(List(BobUserDetails))
  }

  it should "use provided branch and repo to find commits" in {
    // given
    when(repositoriesCache.getBranchCommits(CodebragRepo, MasterBranch)).thenReturn(NoCommitsInBranch)
    when(toReviewFilter.filterCommitsToReview(NoCommitsInBranch, Bob, List(BobUserDetails), CodebragRepo)).thenReturn(List.empty)

    // when
    val context = UserBrowsingContext(Bob.id, CodebragRepo, MasterBranch)
    finder.find(context)
    finder.count(context)

    // then
    verify(repositoriesCache, times(2)).getBranchCommits(CodebragRepo, MasterBranch)
  }

  it should "count commits to review for user default browsing context" in {
    // given
    val defaultContext = UserBrowsingContext(Bob.id, CodebragRepo, MasterBranch)
    when(browsingContextFinder.findUserDefaultContext(Bob.id)).thenReturn(defaultContext)
    when(repositoriesCache.getBranchCommits(CodebragRepo, MasterBranch)).thenReturn(NoCommitsInBranch)
    when(toReviewFilter.filterCommitsToReview(NoCommitsInBranch, Bob, List(BobUserDetails), CodebragRepo)).thenReturn(List.empty)

    // when
    finder.countForUserRepoAndBranch(Bob.id)

    // then
    verify(repositoriesCache).getBranchCommits(CodebragRepo, MasterBranch)
  }

}