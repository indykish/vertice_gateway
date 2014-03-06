/* 
** Copyright [2012-2013] [Megam Systems]
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
** http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package models

import scalaz._
import scalaz.syntax.SemigroupOps
import scalaz.NonEmptyList._
import scalaz.Validation._
import scalaz.effect.IO
import scalaz.EitherT._
import com.twitter.util.Time
import Scalaz._
import controllers.stack._
import controllers.Constants._
import controllers.funnel.FunnelErrors._
import models._
import models.cache._
import com.stackmob.scaliak._
import com.basho.riak.client.query.indexes.{ RiakIndexes, IntIndex, BinIndex }
import com.basho.riak.client.http.util.{ Constants => RiakConstants }
import org.megam.common.riak.{ GSRiak, GunnySack }
import org.megam.common.uid.UID
import net.liftweb.json._
import net.liftweb.json.scalaz.JsonScalaz._
import java.nio.charset.Charset

/**
 * @author rajthilak
 *
 */
/* name, logo, catagory, pricetype, attach, approved, feature-1,2,3,4, plan->{price, description, type}, predefnode */

case class MarketPlacePlan(price: String, description: String, plantype: String) {
  val json = "\"price\":\"" + price + "\",\"description\":\"" + description + "\",\"plantype\":\"" + plantype + "\""
}

case class MarketPlaceFeatures(feature1: String, feature2: String, feature3: String, feature4: String) {
  val json = "\"feature1\":\"" + feature1 + "\",\"feature2\":\"" + feature2 + "\",\"feature3\":\"" + feature3 + "\",\"feature4\":\"" + feature4 + "\""
}

case class MarketPlaceInput(name: String, logo: String, catagory: String, pricetype: String, features: MarketPlaceFeatures, plan: MarketPlacePlan, attach: String, predefnode: String, approved: String) {
  val json = "{\"name\":\"" + name + "\",\"logo\":\"" + logo + "\",\"catagory\":\"" + catagory + "\",\"pricetype\":\"" + pricetype + "\",\"features\":{" + features.json + "},\"plan\":{" + plan.json + "},\"attach\":\"" + attach + "\",\"predefnode\":\"" + predefnode + "\",\"approved\":\"" + approved + "\"}"
}

//init the default market place addons
object MarketPlaceInput {

  val toMap = Map[String, MarketPlaceInput](
    "alfresco" -> MarketPlaceInput("alfresco", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/alfresco.png", "ECM", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 alfresco app", "free"), "false", "predefnode", "false"),
    "diaspora" -> MarketPlaceInput("diaspora", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/diaspora.png", "Social", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 diaspora app", "free"), "false", "predefnode", "false"),
    "dokuwiki" -> MarketPlaceInput("dokuwiki", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/dokuwiki.png", "Wiki", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 dokuwiki app", "free"), "false", "predefnode", "false"),
    "drbd" -> MarketPlaceInput("drbd", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/drbd.png", "DR", "free", new MarketPlaceFeatures("May be used to add redundancy to existing deployments. Fully synchronous, memory synchronous or asynchronous modes of operation. Masking of local IO errors. Shared secret to authenticate the peer upon connect. Bandwidth of background resynchronization tunable.", "Automatic recovery after node, network, or disk failures. Efficient resynchronization, only blocks that were modified during the outage of a node. Short resynchronization time after the crash of an active node, independent of the device size. Automatic detection of the most up-to-date data after complete failure", "Integration scripts for use with Heartbeat. Dependencies to serialize resynchronization, in case of default all devices in parallel. Heartbeat integration to outdate peers with broken replication links, avoids switchovers to stale data. Many tuning parameters allow to optimize DRBD for specific machines, networking hardware, and storage subsystem.", "Existing file systems can be integrated into new DRBD setups without the need of copying. Support for compression of the bitmap exchange. Supports single block device sizes of up to one petabyte. Optional load balancing of read requests."), new MarketPlacePlan("0", "Disaster recovery for 1 app or service", "free"), "true", "predefnode", "true"),
    "dreamfactory" -> MarketPlaceInput("dreamfactory", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/dreamfactory.png", "Mobile Development", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 dreamfactory app", "free"), "false", "predefnode", "false"),
    "drupal" -> MarketPlaceInput("drupal", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/drupal.png", "CMS", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 drupal app", "free"), "false", "predefnode", "false"),
    "elgg" -> MarketPlaceInput("elgg", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/elgg.png", "Social", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 elgg app", "free"), "false", "predefnode", "false"),
    "firepad" -> MarketPlaceInput("firepad", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/firepad.png", "Cloud Editor", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 firepad app", "free"), "false", "predefnode", "false"),
    "ghost" -> MarketPlaceInput("ghost", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/ghost.png", "Blog", "free", new MarketPlaceFeatures("Ghost is an Open Source application which allows you to write and publish your own blog, giving you the tools to make it easy and even fun to do. It's simple, elegant, and designed so that you can spend less time making your blog work and more time blogging.", "Never stop writing, format on the fly. No messing around with *buttons* everywhere. No writing repetitive HTML markup. Mobile friendly.", "Ghost grabs all the important data about your blog and pulls it into one place. No more clicking through tens of browser tabs to view your traffic, social media subscriptions, content performance or news feeds.", "You can write themes for it so your blog can have its own design. You can write plugins for it to add your own functionality. You can host it on your laptop, or you can host it on a public server. The code is open, and so is the MIT license. No restrictions."), new MarketPlacePlan("0", "create 1 ghost app", "free"), "false", "predefnode", "true"),
    "gitlab" -> MarketPlaceInput("gitlab", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/gitlab.png", "Continuous Integration", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 gitlab app", "free"), "false", "predefnode", "false"),
    "hadoop" -> MarketPlaceInput("hadoop", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/hadoop.png", "Business Intelligence", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 hadoop app", "free"), "false", "predefnode", "false"),
    "jenkins" -> MarketPlaceInput("jenkins", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/jenkins.png", "Continuous Integration", "free", new MarketPlaceFeatures("Easy installation: Just java -jar jenkins.war, or deploy it in a servlet container. No additional install, no database. Easy configuration: Jenkins can be configured entirely from its friendly web GUI with extensive on-the-fly error checks and inline help. There's no need to tweak XML manually anymore, although if you'd like to do so, you can do that, too. Change set support: Jenkins can generate a list of changes made into the build from Subversion/CVS. This is also done in a fairly efficient fashion, to reduce the load on the repository.", "Jenkins gives you clean readable URLs for most of its pages, including some permalinks like 'latest build' / 'latest successful build', so that they can be easily linked from elsewhere. Monitor build results by RSS or e-mail to get real-time notifications on failures. Builds can be tagged long after builds are completed.", "JUnit test reports can be tabulated, summarized, and displayed with history information, such as when it started breaking, etc. History trend is plotted into a graph. Jenkins can distribute build/test loads to multiple computers. This lets you get the most out of those idle workstations sitting beneath developers' desks.", "Jenkins can keep track of which build produced which jars, and which build is using which version of jars, and so on. This works even for jars that are produced outside Jenkins, and is ideal for projects to track dependency. Jenkins can be extended via 3rd party plugins. You can write plugins to make Jenkins support tools/processes that your team uses."), new MarketPlacePlan("0", "Use 1 continous integration engine", "free"), "true", "predefnode", "true"),
    "joomla" -> MarketPlaceInput("joomla", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/joomla.png", "CMS", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 joomla app", "free"), "false", "predefnode", "false"),
    "liferay" -> MarketPlaceInput("liferay", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/liferay.png", "Collaboration", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 liferay app", "free"), "false", "predefnode", "false"),
    "magneto" -> MarketPlaceInput("magneto", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/magneto.png", "e-Commerce", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 magneto app", "free"), "false", "predefnode", "false"),
    "mediawiki" -> MarketPlaceInput("mediawiki", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/mediawiki.png", "Wiki", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 mediawiki app", "free"), "false", "predefnode", "false"),
    "openam" -> MarketPlaceInput("openam", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/openam.png", "AM", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 openam app", "free"), "false", "predefnode", "false"),
    "openatrium" -> MarketPlaceInput("openatrium", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/openatrium.png", "Project Management", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 openatrium app", "free"), "false", "predefnode", "false"),
    "opendj" -> MarketPlaceInput("opendj", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/opendj.png", "AM", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 opendj app", "free"), "false", "predefnode", "false"),
    "openerp" -> MarketPlaceInput("openerp", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/openerp.png", "ERP", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 openerp app", "free"), "false", "predefnode", "false"),
    "openldap" -> MarketPlaceInput("openldap", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/openldap.png", "Directory Services", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 openldap app", "free"), "false", "predefnode", "false"),
    "otrs" -> MarketPlaceInput("otrs", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/otrs.png", "HelpDesk", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 otrs app", "free"), "false", "predefnode", "false"),
    "owncloud" -> MarketPlaceInput("owncloud", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/owncloud.png", "Media sharing", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 owncloud app", "free"), "false", "predefnode", "false"),
    "redmine" -> MarketPlaceInput("redmine", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/redmine.png", "Collaboration", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 redmine app", "free"), "false", "predefnode", "false"),
    "reviewboard" -> MarketPlaceInput("reviewboard", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/reviewboard.png", "Collaboration", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 reviewboard app", "free"), "false", "predefnode", "false"),
    "riak" -> MarketPlaceInput("riak", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/riak.png", "DB", "free", new MarketPlaceFeatures("Riak replicates and retrieves data intelligently so it is available for read and write operations, even in failure conditions. You can lose access to many nodes due to network partition or hardware failure without losing data. Add new machines to your Riak cluster easily without incurring a larger operational burden – the same ops tasks apply to small clusters as large clusters.", "Riak automatically distributes data around the cluster and yields a near-linear performance increase as you add capacity. Use Riak’s distributed, full-text search engine with a robust query language. Tag objects stored in Riak with additional values and query by exact match or range.", "Non-key-based querying for large datasets.Data is distributed across nodes using consistent hashing. Consistent hashing ensures data is evenly distributed around the cluster and new nodes can be added automatically, with minimal reshuffling. Riak uses Folsom, an Erlang-based system that collects and reports real-time metrics, to provide stats via HTTP request.", ""), new MarketPlacePlan("0", "Create 1 database", "free"), "true", "predefnode", "true"),
    "scmmanager" -> MarketPlaceInput("scmmanager", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/scmmanager.png", "Development Platform", "free", new MarketPlaceFeatures("Very easy installation. No need to hack configuration files, SCM-Manager is completely configureable from its Web-Interface. No Apache and no database installation is required. Central user, group and permission management.", "Out of the box support for Git, Mercurial and Subversion. Full RESTFul Web Service API (JSON and XML). Rich User Interface. Simple Plugin API. Useful plugins available (f.e. Ldap-, ActiveDirectory-, PAM-Authentication).", "", ""), new MarketPlacePlan("0", "Create 1 scm-manager app", "free"), "true", "predefnode", "true"),
    "sugarcrm" -> MarketPlaceInput("sugarcrm", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/sugarcrm.png", "CRM", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 sugarcrm app", "free"), "false", "predefnode", "false"),
    "thinkup" -> MarketPlaceInput("thinkup", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/thinkup.png", "Social", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 thinkup app", "free"), "false", "predefnode", "false"),
    "trac" -> MarketPlaceInput("trac", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/trac.png", "Bug Tracking", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 trac app", "free"), "false", "predefnode", "false"),
    "twiki" -> MarketPlaceInput("twiki", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/twiki.png", "Wiki", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 twiki app", "free"), "false", "predefnode", "false"),
    "wordpress" -> MarketPlaceInput("wordpress", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/wordpress.png", "CMS", "free", new MarketPlaceFeatures("Start a blog or build a full-fledged website. The only limit is your imagination. Build a great site and spend nothing, zilch, nada. And if you want to make a great site even better, we offer a great selection of premium upgrades. Share your work with the world.", "Publicize lets you connect your WordPress site to the most popular social networks — Facebook, Twitter, Tumblr, LinkedIn, and more. Learn more about your readers, where they’re from, and how they found you. Maps and graphs that beautifully present your stats. Don’t be confined to the desk. Publish near and far with mobile apps for iPhone, iPad, Android, and BlackBerry.", "Your site is hosted on our servers spread across multiple data centers. That way, it’s super fast and always available. Our dashboard is available in over 50 languages and counting.", "Whether you’re searching the forums, reading our support pages, or chatting with a Happiness Engineer, you can always find a helping hand. Keep track of all your favorite blogs and discover new ones with the Reader."), new MarketPlacePlan("0", "Create 1 wordpress app", "free"), "true", "predefnode", "true"),
    "xwiki" -> MarketPlaceInput("xwiki", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/xwiki.png", "Wiki", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 xwiki app", "free"), "false", "predefnode", "false"),
    "zarafa" -> MarketPlaceInput("zarafa", "https://s3-ap-southeast-1.amazonaws.com/megampub/images/market_place_images/zarafa.png", "Firewall", "free", new MarketPlaceFeatures("", "", "", ""), new MarketPlacePlan("0", "Create 1 zarafa app", "free"), "false", "predefnode", "false")
  )

  val toStream = toMap.keySet.toStream

}

case class MarketPlaceResult(id: String, name: String, logo: String, catagory: String, pricetype: String, features: MarketPlaceFeatures, plan: MarketPlacePlan, attach: String, predefnode: String, approved: String, created_at: String) {

  def toJValue: JValue = {
    import net.liftweb.json.scalaz.JsonScalaz.toJSON
    import models.json.MarketPlaceResultSerialization
    val preser = new MarketPlaceResultSerialization()
    toJSON(this)(preser.writer)
  }

  def toJson(prettyPrint: Boolean = false): String = if (prettyPrint) {
    pretty(render(toJValue))
  } else {
    compactRender(toJValue)
  }
}

object MarketPlaceResult {

  def fromJValue(jValue: JValue)(implicit charset: Charset = UTF8Charset): Result[MarketPlaceResult] = {
    import net.liftweb.json.scalaz.JsonScalaz.fromJSON
    import models.json.MarketPlaceResultSerialization
    val preser = new MarketPlaceResultSerialization()
    fromJSON(jValue)(preser.reader)
  }

  def fromJson(json: String): Result[MarketPlaceResult] = (Validation.fromTryCatch {
    parse(json)
  } leftMap { t: Throwable =>
    UncategorizedError(t.getClass.getCanonicalName, t.getMessage, List())
  }).toValidationNel.flatMap { j: JValue => fromJValue(j) }

}

object MarketPlaces {

  implicit val formats = DefaultFormats
  private def riak: GSRiak = GSRiak(MConfig.riakurl, "marketplaces")
  implicit def MarketPlacesSemigroup: Semigroup[MarketPlaceResults] = Semigroup.instance((f1, f2) => f1.append(f2))

  val metadataKey = "MarketPlace"
  val metadataVal = "MarketPlaces Creation"
  val bindex = BinIndex.named("marketplace")

  /**
   * A private method which chains computation to make GunnySack when provided with an input json, email.
   * parses the json, and converts it to nodeinput, if there is an error during parsing, a MalformedBodyError is sent back.
   * After that flatMap on its success and the account id information is looked up.
   * If the account id is looked up successfully, then yield the GunnySack object.
   */
  private def mkGunnySack(email: String, input: String): ValidationNel[Throwable, Option[GunnySack]] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("models.MarketPlaces", "mkGunnySack:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("email", email))
    play.api.Logger.debug(("%-20s -->[%s]").format("json", input))

    val marketPlaceInput: ValidationNel[Throwable, MarketPlaceInput] = (Validation.fromTryCatch {
      parse(input).extract[MarketPlaceInput]
    } leftMap { t: Throwable => new MalformedBodyError(input, t.getMessage) }).toValidationNel //capture failure

    for {
      mkp <- marketPlaceInput
      //TO-DO: Does the leftMap make sense ? To check during function testing, by removing it.
      uir <- (UID(MConfig.snowflakeHost, MConfig.snowflakePort, "mkp").get leftMap { ut: NonEmptyList[Throwable] => ut })
    } yield {
      //TO-DO: do we need a match for None on aor, and uir (confirm it during function testing).
      val bvalue = Set(mkp.name)
      val json = new MarketPlaceResult(uir.get._1 + uir.get._2, mkp.name, mkp.logo, mkp.catagory, mkp.pricetype, mkp.features, mkp.plan, mkp.attach, mkp.predefnode, mkp.approved, Time.now.toString).toJson(false)
      new GunnySack(mkp.name, json, RiakConstants.CTYPE_TEXT_UTF8, None,
        Map(metadataKey -> metadataVal), Map((bindex, bvalue))).some
    }
  }

  private def mkGunnySack_init(input: MarketPlaceInput): ValidationNel[Throwable, Option[GunnySack]] = {
    play.api.Logger.debug("models.MarketPlaces mkGunnySack: entry:\n" + input.json)
    val marketplaceInput: ValidationNel[Throwable, MarketPlaceInput] = (Validation.fromTryCatch {
      parse(input.json).extract[MarketPlaceInput]
    } leftMap { t: Throwable => new MalformedBodyError(input.json, t.getMessage) }).toValidationNel //capture failure

    for {
      mkp <- marketplaceInput
      //TO-DO: Does the leftMap make sense ? To check during function testing, by removing it.
      uir <- (UID(MConfig.snowflakeHost, MConfig.snowflakePort, "mkp").get leftMap { ut: NonEmptyList[Throwable] => ut })
    } yield {
      //TO-DO: do we need a match for uir to filter the None case. confirm it during function testing.
      play.api.Logger.debug("models.marketplaces mkGunnySack: yield:\n" + (uir.get._1 + uir.get._2))
      val bvalue = Set(mkp.name)
      val mkpJson = new MarketPlaceResult(uir.get._1 + uir.get._2, mkp.name, mkp.logo, mkp.catagory, mkp.pricetype, mkp.features, mkp.plan, mkp.attach, mkp.predefnode, mkp.approved, Time.now.toString).toJson(false)
      new GunnySack(mkp.name, mkpJson, RiakConstants.CTYPE_TEXT_UTF8, None,
        Map(metadataKey -> metadataVal), Map((bindex, bvalue))).some
    }
  }

  /*
   * create new market place item with the 'name' of the item provide as input.
   * A index name marketplace name will point to the "marketplaces" bucket
   */
  def create(email: String, input: String): ValidationNel[Throwable, Option[MarketPlaceResult]] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("models.MarketPlaces", "create:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("email", email))
    play.api.Logger.debug(("%-20s -->[%s]").format("json", input))

    (mkGunnySack(email, input) leftMap { err: NonEmptyList[Throwable] =>
      new ServiceUnavailableError(input, (err.list.map(m => m.getMessage)).mkString("\n"))
    }).toValidationNel.flatMap { gs: Option[GunnySack] =>
      (riak.store(gs.get) leftMap { t: NonEmptyList[Throwable] => t }).
        flatMap { maybeGS: Option[GunnySack] =>
          maybeGS match {
            case Some(thatGS) => (parse(thatGS.value).extract[MarketPlaceResult].some).successNel[Throwable]
            case None => {
              play.api.Logger.warn(("%-20s -->[%s]").format("MarketPlace.created success", "Scaliak returned => None. Thats OK."))
              (parse(gs.get.value).extract[MarketPlaceResult].some).successNel[Throwable];
            }
          }
        }
    }
  }

  def marketplace_init: ValidationNel[Throwable, MarketPlaceResults] = {
    play.api.Logger.debug("models.MarketPlaces create: entry")
    (MarketPlaceInput.toMap.some map {
      _.map { p =>
        (mkGunnySack_init(p._2) leftMap { t: NonEmptyList[Throwable] => t
        }).flatMap { gs: Option[GunnySack] =>
          (riak.store(gs.get) leftMap { t: NonEmptyList[Throwable] => t }).
            flatMap { maybeGS: Option[GunnySack] =>
              maybeGS match {
                case Some(thatGS) => MarketPlaceResults(parse(thatGS.value).extract[MarketPlaceResult]).successNel[Throwable]
                case None => {
                  play.api.Logger.warn(("%-20s -->[%s]").format("MarketPlaces.created success", "Scaliak returned => None. Thats OK."))
                  MarketPlaceResults(MarketPlaceResult(new String(), p._2.name, p._2.logo, p._2.catagory, p._2.pricetype, p._2.features, p._2.plan, p._2.attach, p._2.predefnode, p._2.approved, new String())).successNel[Throwable]
                }
              }
            }
        }
      }
    } map {
      _.fold((MarketPlaceResults.empty).successNel[Throwable])(_ +++ _) //fold or foldRight ? 
    }).head //return the folded element in the head.  

  }

  def findByName(marketPlacesNameList: Option[Stream[String]]): ValidationNel[Throwable, MarketPlaceResults] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("models.MarketPlaces", "findByNodeName:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("marketPlaceList", marketPlacesNameList))
    (marketPlacesNameList map {
      _.map { marketplacesName =>
        play.api.Logger.debug("models.MarketPlaceName findByName: marketplaces:" + marketplacesName)
        (riak.fetch(marketplacesName) leftMap { t: NonEmptyList[Throwable] =>
          new ServiceUnavailableError(marketplacesName, (t.list.map(m => m.getMessage)).mkString("\n"))
        }).toValidationNel.flatMap { xso: Option[GunnySack] =>
          xso match {
            case Some(xs) => {
              (Validation.fromTryCatch {
                parse(xs.value).extract[MarketPlaceResult]
              } leftMap { t: Throwable =>
                new ResourceItemNotFound(marketplacesName, t.getMessage)
              }).toValidationNel.flatMap { j: MarketPlaceResult =>
                Validation.success[Throwable, MarketPlaceResults](nels(j.some)).toValidationNel //screwy kishore, every element in a list ? 
              }
            }
            case None => Validation.failure[Throwable, MarketPlaceResults](new ResourceItemNotFound(marketplacesName, "")).toValidationNel
          }
        }
      } // -> VNel -> fold by using an accumulator or successNel of empty. +++ => VNel1 + VNel2
    } map {
      _.foldRight((MarketPlaceResults.empty).successNel[Throwable])(_ +++ _)
    }).head //return the folded element in the head. 

  }

   def listAll: ValidationNel[Throwable, MarketPlaceResults] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("models.MarketPlace", "listAll:Entry"))
    findByName(MarketPlaceInput.toStream.some) //return the folded element in the head.  
  }

   implicit val sedimentPredefResults = new Sedimenter[ValidationNel[Error, MarketPlaceResults]] {
    def sediment(maybeASediment: ValidationNel[Error, MarketPlaceResults]): Boolean = {
      val notSed = maybeASediment.isSuccess
      play.api.Logger.debug("%-20s -->[%s]".format("|^/^|-->MKP:sediment:", notSed))
      notSed
    }
   }
   
}