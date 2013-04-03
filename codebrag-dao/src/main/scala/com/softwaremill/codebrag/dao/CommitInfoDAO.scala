package com.softwaremill.codebrag.dao

import com.softwaremill.codebrag.domain.CommitInfo
import org.bson.types.ObjectId

trait CommitInfoDAO {

  def storeCommit(commit: CommitInfo)

  def findBySha(sha: String): Option[CommitInfo]

  def findByCommitId(commitId: ObjectId): Option[CommitInfo]

  def findAll(): List[CommitInfo]
}