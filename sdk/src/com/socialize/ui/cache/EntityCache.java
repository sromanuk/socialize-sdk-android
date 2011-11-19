/*
 * Copyright (c) 2011 Socialize Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.socialize.ui.cache;

import com.socialize.cache.TTLCache;
import com.socialize.entity.Entity;

/**
 * @author Jason Polites
 *
 */
public class EntityCache extends TTLCache<String, CacheableEntity> {
	
	public EntityCache() {
		super();
	}

	public EntityCache(int initialCapacity) {
		super(initialCapacity);
	}

	public EntityCache(int initialCapacity, int maxCapacity) {
		super(initialCapacity, maxCapacity);
	}

	private long timeToLive = 10 * 60 * 1000; // 10 minutes

	public void setTimeToLive(long timeToLive) {
		this.timeToLive = timeToLive;
	}
	
	public CacheableEntity putEntity(Entity entity) {
		CacheableEntity ce = new CacheableEntity(entity);
		put(entity.getKey(), ce, timeToLive);
		return ce;
	}
	
	public Entity getEntity(String key) {
		CacheableEntity cacheableEntity = get(key);
		if(cacheableEntity != null) {
			return cacheableEntity.getEntity();
		}
		return null;
	}
}
