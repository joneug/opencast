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

import org.opencastproject.elasticsearch.api.SearchIndexException;
import org.opencastproject.elasticsearch.api.SearchResult;
import org.opencastproject.elasticsearch.api.SearchResultItem;
import org.opencastproject.elasticsearch.index.ElasticsearchIndex;
import org.opencastproject.elasticsearch.index.objects.theme.IndexTheme;
import org.opencastproject.elasticsearch.index.objects.theme.ThemeSearchQuery;
import org.opencastproject.list.api.ListProviderException;
import org.opencastproject.list.api.ResourceListProvider;
import org.opencastproject.list.api.ResourceListQuery;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.util.requests.SortCriterion.Order;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component(
    immediate = true,
    service = ResourceListProvider.class,
    property = {
        "service.description=Themes list provider",
        "opencast.service.type=org.opencastproject.index.service.resources.list.provider.ThemesListProvider"
    }
)
public class ThemesListProvider implements ResourceListProvider {

  private static final String PROVIDER_PREFIX = "THEMES";
  public static final String NAME = PROVIDER_PREFIX + ".NAME";
  public static final String DESCRIPTION = PROVIDER_PREFIX + ".DESCRIPTION";

  private static final String[] NAMES = { PROVIDER_PREFIX, NAME, DESCRIPTION };

  private static final Logger logger = LoggerFactory.getLogger(ThemesListProvider.class);

  private ElasticsearchIndex searchIndex;

  private SecurityService securityService;

  @Activate
  protected void activate(BundleContext bundleContext) {
    logger.info("Themes list provider activated!");
  }

  /** OSGi callback for the search index. */
  @Reference(name = "ElasticsearchIndex")
  public void setIndex(ElasticsearchIndex index) {
    this.searchIndex = index;
  }

  /** OSGi callback for the security service. */
  @Reference(name = "SecurityService")
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  @Override
  public String[] getListNames() {
    return NAMES;
  }

  @Override
  public Map<String, String> getList(String listName, ResourceListQuery query)
          throws ListProviderException {
    Map<String, String> list = new HashMap<String, String>();

    if (NAME.equals(listName)) {
      ThemeSearchQuery themeQuery = new ThemeSearchQuery(securityService.getOrganization().getId(),
              securityService.getUser());
      themeQuery.withOffset(query.getOffset().getOrElse(0));
      int limit = query.getLimit().getOrElse(Integer.MAX_VALUE - themeQuery.getOffset());
      themeQuery.withLimit(limit);
      themeQuery.sortByName(Order.Ascending);
      SearchResult<IndexTheme> results = null;
      try {
        results = searchIndex.getByQuery(themeQuery);
      } catch (SearchIndexException e) {
        logger.error("The admin UI Search Index was not able to get the themes", e);
        throw new ListProviderException("No themes list for list name " + listName + " found!");
      }

      for (SearchResultItem<IndexTheme> item : results.getItems()) {
        IndexTheme theme = item.getSource();
        list.put(Long.toString(theme.getIdentifier()), theme.getName());
      }
    }
    else if (DESCRIPTION.equals(listName)) {
      ThemeSearchQuery themeQuery = new ThemeSearchQuery(securityService.getOrganization().getId(),
              securityService.getUser());
      themeQuery.withOffset(query.getOffset().getOrElse(0));
      int limit = query.getLimit().getOrElse(Integer.MAX_VALUE - themeQuery.getOffset());
      themeQuery.withLimit(limit);
      themeQuery.sortByName(Order.Ascending);
      SearchResult<IndexTheme> results = null;
      try {
        results = searchIndex.getByQuery(themeQuery);
      } catch (SearchIndexException e) {
        logger.error("The admin UI Search Index was not able to get the themes", e);
        throw new ListProviderException("No themes list for list name " + listName + " found!");
      }

      for (SearchResultItem<IndexTheme> item : results.getItems()) {
        IndexTheme theme = item.getSource();
        if (theme.getDescription() == null) {
          theme.setDescription("");
        }
        else {
          theme.getDescription();
        }
        list.put(Long.toString(theme.getIdentifier()), theme.getDescription());
      }
    }

    return list;
  }

  @Override
  public boolean isTranslatable(String listName) {
    return false;
  }

  @Override
  public String getDefault() {
    return null;
  }
}
