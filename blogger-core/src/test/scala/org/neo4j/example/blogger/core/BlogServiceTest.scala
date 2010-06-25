package org.neo4j.example.blogger.core

import internal.BlogServiceImpl
import model.{Article, Blog, BlogUser}
import neo4j.Neo4jBlogStorage
import org.specs._
import org.specs.runner._
import java.io.File
import org.apache.commons.io.FileUtils
import java.lang.String
import org.neo4j.kernel.EmbeddedGraphDatabase
import org.neo4j.index.lucene.LuceneIndexService

class BlogServiceTest extends SpecificationWithJUnit("acceptance tests") {

  description = "acceptance testing for user interaction sequences"

  val TEST_DATA_DIR = "testblog"
  var blogService:BlogService = _

  def clearTestDataDir() = { FileUtils.deleteDirectory(new File(TEST_DATA_DIR)) }

  "BlogService implementation" should {

    shareVariables

    doFirst {
      clearTestDataDir()
      // ABKTODO: switch to guice instead of composing by hand
      Neo4jBlogStorage.graphdb = new EmbeddedGraphDatabase(TEST_DATA_DIR)
      Neo4jBlogStorage.index = new LuceneIndexService( Neo4jBlogStorage.graphdb )
      blogService = new BlogServiceImpl {
        blogStorage = new Neo4jBlogStorage 
      }
    }

    doBefore {
      blogService.beginTx
    }
    
    doAfter {
      blogService.succeedTx
    }

    doLast {
      blogService.shutdown;
      clearTestDataDir()
    }

    "create a new user account" in {
      val possibleUser = blogService.createUser("andreas", "abracadabra")

      possibleUser must beSome[BlogUser]
    }
    "created user should exist" in {
      blogService.userExists("andreas") must beTrue
    }
    "should deny re-creating existing user" in {
      val existingUser = blogService.createUser("andreas", "abracadabra")
      val duplicateUser = blogService.createUser("andreas", "abracadabra")

      duplicateUser must beNone
    }
    "sign in user" in {
      val signedInUser = blogService.signIn("andreas", "abracadabra")
      signedInUser must beSome[BlogUser]
    }
    "signed-in user should have expected name and password" in {
      val knownUser = blogService.signIn("andreas", "abracadabra").get
      knownUser.name must beMatching("andreas")
      knownUser.password must beMatching("abracadabra")
    }
    "deny sign-in for unknown user" in {
      val unknownUser = blogService.signIn("nobody", "irrelevant")
      unknownUser must beNone
    }
    "deny sign-in for wrong password" in {
      val unknownUser = blogService.signIn("andreas", "wrongPassword")
      unknownUser must beNone
    }
    "create new blog owned by authenticated user" in {
      val signedInUser = blogService.signIn("andreas", "abracadabra").get
      val blogName = "neo4j-for-eva"
      val blogTitle = "Neo4j for Eva"
      val userBlog = signedInUser.createBlog(blogName, blogTitle)
      userBlog must beSome[Blog]
    }
    "find the created blog" in {
      val foundBlog = blogService.findBlog("neo4j-for-eva")
      foundBlog must beSome[Blog]
    }
    "allow originating user to administrate the created blog" in {
      val originatingUser = blogService.signIn("andreas", "abracadabra").get
      val createdBlog = blogService.findBlog("neo4j-for-eva").get
      originatingUser.canAdministrate(createdBlog) must beTrue
    }
    "disallow known, but unauthorized user to administrate blog" in {
      val unauthorizedUser = blogService.createUser("tiberius", "woof").get
      val someoneElsesBlog = blogService.findBlog("neo4j-for-eva").get
      unauthorizedUser.canAdministrate(someoneElsesBlog) must beFalse
    }
    "grant another user (not the original) administrative access blog" in {
      val authorizedByOriginalAdmin =  blogService.signIn("andreas", "abracadabra").get
      val byFriendOfAdmin = blogService.createUser("delia", "meow").get
      val sharedBlog = blogService.findBlog("neo4j-for-eva").get
      sharedBlog.allowAdministration(byFriendOfAdmin, authorizedByOriginalAdmin) must beTrue
      byFriendOfAdmin.canAdministrate(sharedBlog) must beTrue
    }
    "disallow known, but unauthorized user to self-authorize as an administrator" in {
      val unauthorizedUser = blogService.signIn("tiberius", "woof").get
      val sharedBlog = blogService.findBlog("neo4j-for-eva").get
      sharedBlog.allowAdministration(unauthorizedUser, unauthorizedUser) must beFalse
      unauthorizedUser.canAdministrate(sharedBlog) must beFalse
    }
    "allow originating user to publish to the created blog" in {
      val originatingUser = blogService.signIn("andreas", "abracadabra").get
      val createdBlog = blogService.findBlog("neo4j-for-eva").get
      originatingUser.canPublishTo(createdBlog) must beTrue
    }
    "disallow known, but unauthorized user to publish to blog" in {
      val unauthorizedUser = blogService.signIn("tiberius", "woof").get
      val someoneElsesBlog = blogService.findBlog("neo4j-for-eva").get
      unauthorizedUser.canPublishTo(someoneElsesBlog) must beFalse
    }
    "grant another user (not the owner) publishing access to blog" in {
      val authorizedByOriginalAdmin =  blogService.signIn("andreas", "abracadabra").get
      val byFriendOfAdmin = blogService.signIn("delia", "meow").get
      val sharedBlog = blogService.findBlog("neo4j-for-eva").get
      sharedBlog.allowPublishing(byFriendOfAdmin, authorizedByOriginalAdmin) must beTrue
      byFriendOfAdmin.canPublishTo(sharedBlog) must beTrue
    }
    "disallow known, but unauthorized user to self-authorize as publisher" in {
      val unauthorizedUser = blogService.signIn("tiberius", "woof").get
      val sharedBlog = blogService.findBlog("neo4j-for-eva").get
      sharedBlog.allowPublishing(unauthorizedUser, unauthorizedUser) must beFalse
      unauthorizedUser.canPublishTo(sharedBlog) must beFalse
    }
    "create an article for authenticated user" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val draftArticle = originalAuthor.createArticle("how-to-connect-the-dots", "How to connect the dots")
      draftArticle must beSome[Article]
    }
    "find by name an article that had been created by a user" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val possibleFoundArticle = author.findArticle("how-to-connect-the-dots")
      possibleFoundArticle must beSome[Article]
      possibleFoundArticle.get.name must beMatching("how-to-connect-the-dots")
      possibleFoundArticle.get.title must beMatching("How to connect the dots")
    }
    "mark creating user as article owner (admin) and an author" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val foundArticle = author.findArticle("how-to-connect-the-dots").get
      author.canAuthor(foundArticle) must beTrue
      author.owns(foundArticle) must beTrue
    }
    "edit an article using a working copy" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val foundArticle = author.findArticle("how-to-connect-the-dots").get
      val workingCopy = author.edit(foundArticle).get
      workingCopy.copyOf.equals(foundArticle) must beTrue
      workingCopy.body = "Start by marking dots on a page."
      foundArticle.body must notBeMatching (workingCopy.body)
    }
    "editing returns existing working copy" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val foundArticle = author.findArticle("how-to-connect-the-dots").get
      val workingCopy = author.edit(foundArticle).get
      workingCopy.body must beMatching("Start by marking dots on a page.")
    }
    "cancel edits to an article" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val foundArticle = author.findArticle("how-to-connect-the-dots").get
      val workingCopy = author.edit(foundArticle).get
      workingCopy.cancel must beTrue
      val newWorkingCopy = author.edit(foundArticle).get
      newWorkingCopy.body must notBeMatching("Start by marking dots on a page.")
    }
    "save a working copy" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val foundArticle = author.findArticle("how-to-connect-the-dots").get
      val workingCopy = author.edit(foundArticle).get
      workingCopy.body = "Start by marking dots, then connect them however you'd like."
      val possibleSavedArticle = workingCopy.save
      possibleSavedArticle must beSome[Article]
      possibleSavedArticle.get.body must beMatching("Start by marking dots, then connect them however you'd like.")
      val refoundArticle = author.findArticle("how-to-connect-the-dots").get
      refoundArticle.equals(foundArticle) must beFalse
      refoundArticle.equals(possibleSavedArticle.get) must beTrue
    }
    "not show working copies in article list" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val foundArticle = author.findArticle("how-to-connect-the-dots").get
      val workingCopy = author.edit(foundArticle).get
      val allArticles = author.allArticles
      allArticles must notContain(workingCopy)
    }
    "hide the original article name when saving a renamed working copy" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val foundArticle = author.findArticle("how-to-connect-the-dots").get
      val workingCopy = author.edit(foundArticle).get
      workingCopy.name = "i-see-dots"
      workingCopy.save must beSome[Article]
      val originalNamedArticle = author.findArticle("how-to-connect-the-dots")
      originalNamedArticle must beNone
      val renamedArticle = author.findArticle("i-see-dots")
      renamedArticle must beSome[Article]
    }
    "publish a newly created article" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val helloArticle = author.createArticle("hello", "Hello Blogosphere").get

      val authorsBlog = blogService.findBlog("neo4j-for-eva").get
      authorsBlog.publish(helloArticle, author) must beTrue
      helloArticle.publishedIn must contain(authorsBlog)
    }
    "prevent edits to a published artice" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val publishedArticle = author.findArticle("hello").get
      val possibleWorkingCopy = author.edit(publishedArticle)
      possibleWorkingCopy must beNone
    }
    "publish an article with saved edits" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val helloArticle = author.createArticle("follow-up", "Hello again").get
      val workingCopy = author.edit(helloArticle).get
      workingCopy.body = "I am a man of few words."
      val revisedArticle = workingCopy.save.get

      val authorsBlog = blogService.findBlog("neo4j-for-eva").get
      authorsBlog.publish(revisedArticle, author) must beTrue
      revisedArticle.publishedIn must contain(authorsBlog)
    }
    "prevent publishing an article with extant working copies" in {
      val author = blogService.signIn("andreas", "abracadabra").get
      val connectDotsArticle = author.findArticle("i-see-dots").get
      val workingCopy = author.edit(connectDotsArticle).get

      val authorsBlog = blogService.findBlog("neo4j-for-eva").get
      authorsBlog.publish(connectDotsArticle, author) must beFalse
    }
    "prevent publishing a working copy of an article" in {
      // ABKTODO: also, tigtening up the types would prevent someone from trying
      val author = blogService.signIn("andreas", "abracadabra").get
      val connectDotsArticle = author.findArticle("i-see-dots").get 
      val workingCopy = author.edit(connectDotsArticle).get

      val authorsBlog = blogService.findBlog("neo4j-for-eva").get
      authorsBlog.publish(workingCopy, author) must beFalse
    }
    "add another author to an un-published article" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val secondAuthor = blogService.signIn("delia", "meow").get
      val sharedArticle = originalAuthor.findArticle("i-see-dots").get
      sharedArticle.addAuthor(secondAuthor, originalAuthor) must beTrue
      val sameArticleAsShared = secondAuthor.findArticle("i-see-dots")
      sameArticleAsShared must beSome[Article]
      sameArticleAsShared.get must beEqual(sharedArticle)
    }
    "remove an author from an un-published article" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val secondAuthor = blogService.signIn("delia", "meow").get
      val sharedArticle = originalAuthor.findArticle("i-see-dots").get
      sharedArticle.removeAuthor(secondAuthor, originalAuthor) must beTrue
      val unsharedArticle = secondAuthor.findArticle("i-see-dots")
      unsharedArticle must beNone
    }
    "refuse to add an author to a published article" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val secondAuthor = blogService.signIn("delia", "meow").get
      val publishedArticle = originalAuthor.findArticle("hello").get
      publishedArticle.addAuthor(secondAuthor, originalAuthor) must beFalse
    }
    "provide each author with their own working copy" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val secondAuthor = blogService.signIn("delia", "meow").get
      val sharedArticle = originalAuthor.findArticle("i-see-dots").get
      sharedArticle.addAuthor(secondAuthor, originalAuthor)
      val originalWorkingCopy = originalAuthor.edit(sharedArticle)
      originalWorkingCopy must beSome[Article]
      val secondWorkingCopy = secondAuthor.edit(sharedArticle)
      secondWorkingCopy must beSome[Article]
      originalWorkingCopy.equals(secondWorkingCopy) must beFalse
    }
    "prevent a working copy from saving when merge needed" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val secondAuthor = blogService.signIn("delia", "meow").get
      val sharedArticle = originalAuthor.findArticle("i-see-dots").get
      val originalWorkingCopy = originalAuthor.edit(sharedArticle).get
      val secondWorkingCopy = secondAuthor.edit(sharedArticle).get

      originalWorkingCopy.body = "Dots are everywhere."
      originalWorkingCopy.save must beSome[Article]
      secondWorkingCopy.save must beNone
    }
    "prevent publishing a multi-author article with extant working copies" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val secondAuthor = blogService.signIn("delia", "meow").get
      val sharedArticle = originalAuthor.findArticle("i-see-dots").get
      val originalWorkingCopy = originalAuthor.edit(sharedArticle).get
      val secondWorkingCopy = secondAuthor.edit(sharedArticle).get
      val originalAuthorsBlog = blogService.findBlog("neo4j-for-eva").get

      originalAuthorsBlog.publish(sharedArticle, originalAuthor) must beFalse
    }
    "merge changes to current article version to working copy" in {
      val originalAuthor = blogService.signIn("andreas", "abracadabra").get
      val secondAuthor = blogService.signIn("delia", "meow").get
      val sharedArticle = originalAuthor.findArticle("i-see-dots").get
      val secondWorkingCopy = secondAuthor.edit(sharedArticle).get
      val mergedWorkingCopy = secondWorkingCopy.merge
      mergedWorkingCopy.save must beSome[Article]
    }
    "allow secondary author to publish to a separate blog" in {
      val secondAuthor = blogService.signIn("delia", "meow").get
      val sharedArticle = secondAuthor.findArticle("i-see-dots").get
      val secondBlog = secondAuthor.createBlog("unoriginal-thoughts", "The works of a co-author").get
      secondBlog.publish(sharedArticle, secondAuthor) must beTrue
    }
  }
  
}