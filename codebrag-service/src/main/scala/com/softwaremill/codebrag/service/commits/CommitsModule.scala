package com.softwaremill.codebrag.service.commits

import com.softwaremill.codebrag.service.commits.jgit._
import com.softwaremill.codebrag.dao.{RepositoryHeadStore, UserDAO, CommitInfoDAO}
import com.softwaremill.codebrag.common.EventBus
import com.softwaremill.codebrag.service.config.{RepositoryConfig, CodebragConfig}

trait CommitsModule {
  lazy val commitImportService = new CommitImportService(
    new JgitCommitsLoader(
      new JgitFacade,
      new InternalGitDirTree(config),
      new JgitLogConverter,
      repoHeadStore),
    commitInfoDao,
    eventBus)

  lazy val repoDataProducer = new RepoDataProducer(userDao, config)

  def commitInfoDao: CommitInfoDAO
  def repoHeadStore: RepositoryHeadStore
  def userDao: UserDAO
  def eventBus: EventBus
  def config: CodebragConfig with RepositoryConfig
}