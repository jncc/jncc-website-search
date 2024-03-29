const esb = require('elastic-builder')

const sortOptions = {
    RELEVANCE: 'relevance',
    TITLE_ASC: 'title_asc',
    TITLE_DESC: 'title_desc',
    DATE_ASC: 'date_asc',
    DATE_DESC: 'date_desc'
}

const viewOptions = {
    PAGES: 'pages',
    RESOURCES: 'resources'
}

function buildEsPageQuery(queryParams) {
    var requestBody = esb.requestBodySearch()
        .query(
            esb.boolQuery()
                .should(getSearchTermQueries(queryParams.queryTerms))
                .mustNot([
                    esb.existsQuery('resource_type'),
                    esb.existsQuery('file_extension')
                ])
                .minimumShouldMatch(1)
        )
        .from(getPageStartIndex(queryParams.page, queryParams.pageSize))
        .size(queryParams.pageSize)
        .source({
            'includes': [ '*' ],
            'excludes': [ 'content' ]
        })
        .highlight(esb.highlight()
            .preTags('<b>')
            .postTags('</b>')
            .field('content')
            .numberOfFragments(2)
            .scoreOrder('_score')
        )
        .sort(getSortQuery(queryParams.sort))
        // todo: ideally we would also have "track_total_hits": true

    return requestBody.toJSON()
}

function buildEsResourceQuery(queryParams) {

    var requestBody = esb.requestBodySearch()
        .query(
            esb.boolQuery()
                .must([
                    esb.boolQuery()
                        .should(getSearchTermQueries(queryParams.queryTerms))
                        .minimumShouldMatch(1)
                ])
                .filter(esb.existsQuery('resource_type'))
        )
        .postFilter( // post filter so that the aggregation counts aren't affected
            esb.boolQuery()
                .should(getFileFilterQueries(queryParams.filters))
                .minimumShouldMatch(1)
        )
        .aggs([
            esb.termsAggregation('file_types', 'file_extension.keyword'),
            esb.missingAggregation('other', 'file_extension.keyword')
        ])
        .from(getPageStartIndex(queryParams.page, queryParams.pageSize))
        .size(queryParams.pageSize)
        .source({
            'includes': [ '*' ],
            'excludes': [ 'content' ]
        })
        .highlight(esb.highlight()
            .preTags('<b>')
            .postTags('</b>')
            .field('content')
            .numberOfFragments(2)
            .scoreOrder('_score')
        )
        .sort(getSortQuery(queryParams.sort))

    return requestBody.toJSON()
}

function getPageStartIndex(page, pageSize) {
    if (page > 1) {
        return (page-1)*pageSize
    }

    return 0;
}

function getSearchTermQueries(queryTerms) {
    var termQueries = []
    queryTerms.forEach(term => {
        termQueries.push(
            esb.commonTermsQuery('title', term)
                .cutoffFrequency(0.001)
                .lowFreqOperator("or")
        )
        termQueries.push(
            esb.commonTermsQuery('content', term)
                .cutoffFrequency(0.001)
                .lowFreqOperator("or")
        )
    })

    return termQueries
}

function getFileFilterQueries(filters) {
    var queryFilters = []
    filters.forEach(filter => {
        if (filter === 'other') {
            // assets with no files
            queryFilters.push(esb.boolQuery()
                .mustNot(esb.existsQuery('file_extension')))
        } else {
            queryFilters.push(esb.termQuery('file_extension.keyword', filter))
        }
    })

    return queryFilters
}

function getSortQuery(sortOption) {
    switch (sortOption) {
        case sortOptions.RELEVANCE:
            return esb.sort('_score')
        case sortOptions.TITLE_ASC:
            return esb.sort('title.keyword', "asc")
        case sortOptions.TITLE_DESC:
            return esb.sort('title.keyword', "desc")
        case sortOptions.DATE_ASC:
            return esb.sort('published_date', "asc")
        case sortOptions.DATE_DESC:
            return esb.sort('published_date', "desc")
        default:
            console.warn(`Sort option ${sortOption} was not recognised, query not changed`)
            return esb.sort('_score')
    }
}

module.exports = {
    sortOptions: sortOptions,
    viewOptions: viewOptions,
    buildEsPageQuery: buildEsPageQuery,
    buildEsResourceQuery: buildEsResourceQuery
}