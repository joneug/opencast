/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.index.service.resources.list.provider;

import org.opencastproject.elasticsearch.index.ElasticsearchIndex;
import org.opencastproject.elasticsearch.index.objects.event.Event;
import org.opencastproject.elasticsearch.index.objects.event.EventIndexSchema;
import org.opencastproject.elasticsearch.index.objects.series.Series;
import org.opencastproject.elasticsearch.index.objects.series.SeriesIndexSchema;
import org.opencastproject.list.api.ResourceListProvider;
import org.opencastproject.list.api.ResourceListQuery;
import org.opencastproject.list.util.ListProviderUtil;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserDirectoryService;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@Component(
    immediate = true,
    service = ResourceListProvider.class,
    property = {
        "service.description=Contributors list provider",
        "opencast.service.type=org.opencastproject.index.service.resources.list.provider.ContributorsListProvider"
    }
)
public class ContributorsListProvider implements ResourceListProvider {

  private static final String CONFIGURATION_KEY_EXCLUDE_USER_PROVIDER = "exclude.user.provider";
  private static final String ALL_USER_PROVIDERS_VALUE = "*";

  private static final String PROVIDER_PREFIX = "CONTRIBUTORS";

  public static final String DEFAULT = PROVIDER_PREFIX;
  public static final String NAMES_TO_USERNAMES = PROVIDER_PREFIX + ".NAMES.TO.USERNAMES";
  public static final String USERNAMES = PROVIDER_PREFIX + ".USERNAMES";

  protected static final String[] NAMES = { PROVIDER_PREFIX, USERNAMES, NAMES_TO_USERNAMES };

  private static final Logger logger = LoggerFactory.getLogger(ContributorsListProvider.class);

  private final Set<String> excludeUserProvider = new HashSet<>();
  private UserDirectoryService userDirectoryService;
  private ElasticsearchIndex searchIndex;

  @Activate
  protected void activate(Map<String, Object> properties) {
    modified(properties);
    logger.info("Contributors list provider activated!");
  }

  @Modified
  public void modified(Map<String, Object> properties) {
    Object excludeUserProviderValue = properties.get(CONFIGURATION_KEY_EXCLUDE_USER_PROVIDER);
    excludeUserProvider.clear();
    if (excludeUserProviderValue != null) {
      for (String userProvider : StringUtils.split(excludeUserProviderValue.toString(), ',')) {
        if (StringUtils.trimToNull(userProvider) != null) {
          excludeUserProvider.add(StringUtils.trimToNull(userProvider));
        }
      }
    }
    logger.debug("Excluded user providers: {}", excludeUserProvider);
  }

  /** OSGi callback for users services. */
  @Reference(name = "userDirectoryService")
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  /** OSGi callback for the search index. */
  @Reference(name = "ElasticsearchIndex")
  public void setIndex(ElasticsearchIndex index) {
    this.searchIndex = index;
  }

  @Override
  public String[] getListNames() {
    return NAMES;
  }

  @Override
  public Map<String, String> getList(String listName, ResourceListQuery query) {
    if (listName.equalsIgnoreCase(USERNAMES)) {
      return getListWithUserNames(query);
    } else if (listName.equalsIgnoreCase(NAMES_TO_USERNAMES)) {
      return getListWithTechnicalPresenters(query);
    } else {
      return getList(query);
    }
  }

  /**
   * Get all of the contributors with friendly printable names.
   *
   * @param query
   *          The query for the list including limit and offset.
   * @return The {@link Map} including all of the contributors.
   */
  protected Map<String, String> getList(ResourceListQuery query) {
    Map<String, String> usersList = new HashMap<String, String>();
    int offset = 0;
    int limit = 0;
    SortedSet<String> contributorsList = new TreeSet<String>(new Comparator<String>() {
      @Override
      public int compare(String name1, String name2) {
        return name1.compareTo(name2);
      }
    });

    if (!excludeUserProvider.contains(ALL_USER_PROVIDERS_VALUE)) {
      Iterator<User> users = userDirectoryService.findUsers("%", offset, limit);
      while (users.hasNext()) {
        User u = users.next();
        if (!excludeUserProvider.contains(u.getProvider()) && StringUtils.isNotBlank(u.getName()))
          contributorsList.add(u.getName());
      }
    }

    contributorsList.addAll(searchIndex.getTermsForField(EventIndexSchema.CONTRIBUTOR,
            Event.DOCUMENT_TYPE));
    contributorsList.addAll(searchIndex.getTermsForField(EventIndexSchema.PRESENTER,
            Event.DOCUMENT_TYPE));
    contributorsList.addAll(searchIndex.getTermsForField(EventIndexSchema.PUBLISHER,
            Event.DOCUMENT_TYPE));
    contributorsList.addAll(searchIndex.getTermsForField(SeriesIndexSchema.CONTRIBUTORS,
            Series.DOCUMENT_TYPE));
    contributorsList.addAll(searchIndex.getTermsForField(SeriesIndexSchema.ORGANIZERS,
            Series.DOCUMENT_TYPE));
    contributorsList.addAll(searchIndex.getTermsForField(SeriesIndexSchema.PUBLISHERS,
            Series.DOCUMENT_TYPE));

    // TODO: TThis is not a good idea.
    // TODO: The search index can handle limit and offset.
    // TODO: We should not request all data.
    if (query != null) {
      if (query.getLimit().isSome())
        limit = query.getLimit().get();

      if (query.getOffset().isSome())
        offset = query.getOffset().get();
    }

    int i = 0;

    for (String contributor : contributorsList) {
      if (i >= offset && (limit == 0 || i < limit)) {
        usersList.put(contributor, contributor);
      }
      i++;
    }

    return usersList;
  }

  /**
   * Get the contributors list including usernames and organizations for the users available.
   *
   * @param query
   *          The query for the list including limit and offset.
   * @return The {@link Map} including all of the contributors.
   */
  protected Map<String, String> getListWithTechnicalPresenters(ResourceListQuery query) {
    int offset = 0;
    int limit = 0;

    List<Contributor> contributorsList = new ArrayList<Contributor>();

    HashSet<String> labels = new HashSet<String>();

    if (!excludeUserProvider.contains(ALL_USER_PROVIDERS_VALUE)) {
      Iterator<User> users = userDirectoryService.findUsers("%", offset, limit);
      while (users.hasNext()) {
        User u = users.next();
        if (!excludeUserProvider.contains(u.getProvider())) {
          if (StringUtils.isNotBlank(u.getName())) {
            contributorsList.add(new Contributor(u.getUsername(), u.getName()));
            labels.add(u.getName());
          } else {
            contributorsList.add(new Contributor(u.getUsername(), u.getUsername()));
            labels.add(u.getUsername());
          }
        }
      }
    }

    addIndexNamesToMap(labels, contributorsList, searchIndex
            .getTermsForField(EventIndexSchema.PRESENTER, Event.DOCUMENT_TYPE));
    addIndexNamesToMap(labels, contributorsList, searchIndex
            .getTermsForField(EventIndexSchema.CONTRIBUTOR, Event.DOCUMENT_TYPE));
    addIndexNamesToMap(labels, contributorsList, searchIndex
            .getTermsForField(SeriesIndexSchema.CONTRIBUTORS, Event.DOCUMENT_TYPE));
    addIndexNamesToMap(labels, contributorsList, searchIndex
            .getTermsForField(SeriesIndexSchema.ORGANIZERS, Event.DOCUMENT_TYPE));
    addIndexNamesToMap(labels, contributorsList, searchIndex
            .getTermsForField(SeriesIndexSchema.PUBLISHERS, Event.DOCUMENT_TYPE));

    Collections.sort(contributorsList, new Comparator<Contributor>() {
      @Override
      public int compare(Contributor contributor1, Contributor contributor2) {
        return contributor1.getLabel().compareTo(contributor2.getLabel());
      }
    });

    Map<String, String> contributorMap = new LinkedHashMap<>();
    for (Contributor contributor : contributorsList) {
      contributorMap.put(contributor.getKey(), contributor.getLabel());
    }

    return ListProviderUtil.filterMap(contributorMap, query);
  }

  /**
   * Get the contributors list including usernames and organizations for the users available.
   *
   * @param query
   *          The query for the list including limit and offset.
   * @return The {@link Map} including all of the contributors.
   */
  protected Map<String, String> getListWithUserNames(ResourceListQuery query) {

    int offset = 0;
    int limit = 0;

    List<Contributor> contributorsList = new ArrayList<Contributor>();

    HashSet<String> labels = new HashSet<String>();

    if (!excludeUserProvider.contains(ALL_USER_PROVIDERS_VALUE)) {
      Iterator<User> users = userDirectoryService.findUsers("%", offset, limit);
      while (users.hasNext()) {
        User u = users.next();
        if (!excludeUserProvider.contains(u.getProvider())) {
          if (StringUtils.isNotBlank(u.getName())) {
            contributorsList.add(new Contributor(u.getUsername(), u.getName()));
            labels.add(u.getName());
          } else {
            contributorsList.add(new Contributor(u.getUsername(), u.getUsername()));
            labels.add(u.getUsername());
          }
        }
      }
    }

    Collections.sort(contributorsList, new Comparator<Contributor>() {
      @Override
      public int compare(Contributor contributor1, Contributor contributor2) {
        return contributor1.getLabel().compareTo(contributor2.getLabel());
      }
    });

    Map<String, String> contributorMap = new LinkedHashMap<>();
    for (Contributor contributor : contributorsList) {
      contributorMap.put(contributor.getKey(), contributor.getLabel());
    }

    return ListProviderUtil.filterMap(contributorMap, query);
  }

  /**
   * Add all names in the index to the map if they aren't already present as a user.
   *
   * @param userLabels
   *          The collection of user labels, the full names of the users.
   * @param contributors
   *          The collection of all contributors including the index names that will be added.
   * @param indexNames
   *          The list of new names from the index.
   */
  protected void addIndexNamesToMap(Set<String> userLabels, Collection<Contributor> contributors,
          List<String> indexNames) {
    for (String indexName : indexNames) {
      if (!userLabels.contains(indexName)) {
        contributors.add(new Contributor(indexName, indexName));
      }
    }
  }

  @Override
  public boolean isTranslatable(String listName) {
    return false;
  }

  @Override
  public String getDefault() {
    return null;
  }

  private class Contributor {
    public String getKey() {
      return key;
    }

    public String getLabel() {
      return label;
    }

    private String key;
    private String label;

    Contributor(String key, String label) {
      this.key = key;
      this.label = label;
    }

    @Override
    public String toString() {
      return key + ":" + label;
    }
  }
}
