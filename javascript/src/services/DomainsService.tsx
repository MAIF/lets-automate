

export interface Record {
    id: number;
    target: string;
    ttl: number;
    fieldType: string;
    subDomain: string;
}

export interface Domain {
    name: string;
    records: Record[];
}


export function listDomains(): Promise<Map<string, Domain>> {
    return fetch(`/api/domains`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json'
            }
        })
        .then(res => {
            if (res.status === 200) {
                return res.json().then((json: Domain[]) => {
                    return new Map<string, Domain>(json.map(toTuple));
                })
            } else {
                return res.text().then( t => Promise.reject(t));
            }
        });
}


function toTuple(c: Domain): [string, Domain]{
    return [c.name, c];
}

