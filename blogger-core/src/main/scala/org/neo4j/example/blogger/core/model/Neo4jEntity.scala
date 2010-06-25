package org.neo4j.example.blogger.core.model

import scalaz._
import Scalaz._

import org.neo4j.index.IndexService
import org.neo4j.graphdb.{GraphDatabaseService, RelationshipType, Node}
import org.neo4j.example.blogger.core.neo4j.Neo4jBlogStorage

class Neo4jEntity(val underlyingNode:Node) {

  private[neo4j] def hasRelationshipTo(entity:Neo4jEntity, ofType:RelationshipType):Boolean = {
    underlyingNode.getRelationships(ofType).exists( (r) => {
      r.getEndNode.equals(entity.underlyingNode)
    })
  }

  private[neo4j] def createRelationshipFrom(user:Option[BlogUser], relationship:RelationshipType):Boolean = {
    user.exists( (u) => {
      u.underlyingNode.createRelationshipTo(underlyingNode, relationship)
      true
    })
  }

  private[neo4j] def delete = {
    underlyingNode.getRelationships.foreach(r => r.delete)
    underlyingNode.delete
  }

  override def equals(that:Any) = {
    that match {
      case (e:Neo4jEntity) => (e.underlyingNode == underlyingNode)
      case _ => false
    }
  }

  override def hashCode() = {
    underlyingNode.hashCode
  }
}